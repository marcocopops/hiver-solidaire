package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.notification.MailComposer.Composed;
import com.marcantoinemeunier.hiversolidaire.notification.MailComposer.Ready;
import com.marcantoinemeunier.hiversolidaire.notification.MailComposer.Skipped;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.AbandonReason;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Kind;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envoi des lignes dues de la table d'envois (ADR-0012), en trois temps : une transaction courte les réserve et les
 * ferme ; le contenu est calculé, puis l'e-mail envoyé hors transaction (un serveur SMTP lent ne bloque aucune
 * inscription) ; une transaction enregistre l'issue. Un échec est réessayé plus tard, au plus {@value #MAX_ATTEMPTS}
 * fois et jamais après la limite de la ligne.
 * <p>
 * Logs : identifiant de la ligne, type, nombre d'essais et classe de l'exception, jamais une adresse ni un contenu (le
 * message d'une exception SMTP peut contenir l'adresse du destinataire).
 */
@Component
class MailDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MailDispatcher.class);

    static final int MAX_ATTEMPTS = 6;
    /** Délai avant le nouvel essai qui suit le n-ième échec. */
    static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofMinutes(60));
    private static final int BATCH = 50;

    private final MailDeliveries deliveries;
    private final MailTransport transport;
    private final Map<Kind, MailComposer> composers = new EnumMap<>(Kind.class);
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;
    private final Clock clock;

    MailDispatcher(MailDeliveries deliveries, MailTransport transport, List<MailComposer> composers,
            PlatformTransactionManager transactions, Clock clock) {
        this.deliveries = deliveries;
        this.transport = transport;
        composers.forEach(composer -> this.composers.put(composer.kind(), composer));
        this.writes = new TransactionTemplate(transactions);
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
        this.clock = clock;
    }

    /** Envoie tout ce qui est dû. Sans canal d'envoi, les lignes attendent, et celles qui ont expiré sont abandonnées. */
    void dispatchDue() {
        if (!transport.available()) {
            writes.executeWithoutResult(status -> deliveries.abandonExpired(clock.instant()));
            return;
        }
        List<UUID> due;
        do {
            due = writes.execute(status -> deliveries.claimDue(clock.instant(), BATCH));
            due.forEach(this::process);
        } while (due.size() == BATCH);
    }

    private record Prepared(MailDelivery delivery, Composed composed) {
    }

    private void process(UUID id) {
        Prepared prepared;
        try {
            prepared = reads.execute(status -> deliveries.pending(id).map(this::prepare).orElse(null));
        } catch (RuntimeException e) {
            LOG.warn("Mail delivery {} could not be composed ({})", id, e.getClass().getSimpleName());
            writes.executeWithoutResult(status -> deliveries.pending(id).ifPresent(this::recordFailure));
            return;
        }
        if (prepared == null) {
            return;
        }
        var delivery = prepared.delivery();
        switch (prepared.composed()) {
            case Skipped skipped -> {
                writes.executeWithoutResult(status -> deliveries.abandon(id, skipped.reason(), clock.instant()));
                LOG.info("Mail delivery {} ({}) abandoned: {}", id, delivery.kind(), skipped.reason());
            }
            case Ready ready -> send(delivery, ready.mail());
        }
    }

    private Prepared prepare(MailDelivery delivery) {
        var now = clock.instant();
        if (now.isAfter(delivery.notAfter())) {
            return new Prepared(delivery, new Skipped(AbandonReason.EXPIRED));
        }
        var composer = composers.get(delivery.kind());
        if (composer == null) {
            throw new IllegalStateException("No composer for " + delivery.kind());
        }
        return new Prepared(delivery, composer.compose(delivery, now));
    }

    private void send(MailDelivery delivery, OutgoingMail mail) {
        try {
            transport.send(mail);
        } catch (RuntimeException e) {
            // Seule la classe de l'exception : son message peut contenir l'adresse du destinataire.
            LOG.warn("Mail delivery {} ({}) failed, attempt {} ({})", delivery.id(), delivery.kind(),
                    delivery.attempts() + 1, e.getClass().getSimpleName());
            writes.executeWithoutResult(status -> recordFailure(delivery));
            return;
        }
        writes.executeWithoutResult(status -> deliveries.markSent(delivery.id(), clock.instant()));
        LOG.info("Mail delivery {} ({}) sent", delivery.id(), delivery.kind());
    }

    /** Nouvel essai plus tard, ou abandon après {@value #MAX_ATTEMPTS} échecs. */
    private void recordFailure(MailDelivery delivery) {
        var failures = delivery.attempts() + 1;
        var now = clock.instant();
        if (failures >= MAX_ATTEMPTS) {
            deliveries.recordFailure(delivery.id(), now);
            deliveries.abandon(delivery.id(), AbandonReason.FAILED, now);
            LOG.warn("Mail delivery {} ({}) abandoned after {} failed attempts", delivery.id(), delivery.kind(),
                    failures);
            return;
        }
        deliveries.recordFailure(delivery.id(), now.plus(RETRY_DELAYS.get(failures - 1)));
    }
}
