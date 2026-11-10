package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Change;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmations aux bénévoles (§6), écrites dans la transaction de l'inscription ou de la désinscription : elles
 * n'existent que si le changement a bien eu lieu. Les changements d'un bénévole se regroupent dans un seul e-mail, qui
 * part {@code app.mail.confirmation-delay} (10 minutes) après le dernier : s'inscrire à cinq journées donne un e-mail.
 * Rien pour un bénévole sans adresse (inscrit par téléphone) ni pour un compte supprimé.
 */
@Component
public class MailOutbox {

    /** Au-delà, une confirmation qui n'a pas pu partir n'a plus de sens. */
    static final Duration CONFIRMATION_LIFETIME = Duration.ofHours(48);

    private final MailDeliveries deliveries;
    private final Clock clock;
    private final Duration delay;

    MailOutbox(MailDeliveries deliveries, Clock clock,
            @Value("${app.mail.confirmation-delay:PT10M}") Duration delay) {
        this.deliveries = deliveries;
        this.clock = clock;
        this.delay = delay;
    }

    /** Inscription enregistrée, par le bénévole ou par un coordinateur. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registered(UUID volunteerId, UUID registrationId, boolean byCoordinator) {
        record(volunteerId, registrationId, Change.REGISTERED, byCoordinator);
    }

    /** Inscription annulée, par le bénévole ou par un coordinateur (pas par la suppression du compte). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelled(UUID volunteerId, UUID registrationId, boolean byCoordinator) {
        record(volunteerId, registrationId, Change.CANCELLED, byCoordinator);
    }

    /** Suppression du compte : les e-mails qui attendaient ne partent plus. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(UUID volunteerId) {
        deliveries.abandonPendingOf(volunteerId, clock.instant());
    }

    private void record(UUID volunteerId, UUID registrationId, Change change, boolean byCoordinator) {
        if (deliveries.activeEmail(volunteerId).isEmpty()) {
            return;
        }
        var now = clock.instant();
        deliveries.addToOpenConfirmation(volunteerId, registrationId, change, byCoordinator, now, now.plus(delay),
                now.plus(delay).plus(CONFIRMATION_LIFETIME));
    }
}
