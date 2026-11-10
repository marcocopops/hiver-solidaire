package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.notification.MailDeliveries.ChangeRow;
import com.marcantoinemeunier.hiversolidaire.notification.MailDeliveries.RegistrationRow;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.AbandonReason;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Change;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Kind;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Confirmation des inscriptions et désinscriptions d'un bénévole (§6, « S'inscrire » étape 5), regroupées : l'e-mail
 * donne l'état final des changements de la période. Une inscription créée puis annulée avant l'envoi n'apparaît pas ;
 * une soirée passée non plus. S'il ne reste rien, aucun e-mail.
 */
@Component
class ConfirmationMail implements MailComposer {

    private final MailDeliveries deliveries;
    private final RegistrationLines lines;
    private final MailCommons commons;
    private final MailRenderer renderer;

    ConfirmationMail(MailDeliveries deliveries, RegistrationLines lines, MailCommons commons, MailRenderer renderer) {
        this.deliveries = deliveries;
        this.lines = lines;
        this.commons = commons;
        this.renderer = renderer;
    }

    @Override
    public Kind kind() {
        return Kind.CONFIRMATION;
    }

    @Override
    public Composed compose(MailDelivery delivery, Instant now) {
        var email = deliveries.activeEmail(delivery.volunteerId());
        if (email.isEmpty()) {
            return new Skipped(AbandonReason.NO_RECIPIENT);
        }
        var changes = deliveries.changes(delivery.id());
        var registered = ids(changes, Change.REGISTERED);
        var cancelled = ids(changes, Change.CANCELLED);
        var byCoordinator = changes.stream().filter(ChangeRow::byCoordinator).map(ChangeRow::registrationId)
                .collect(Collectors.toSet());
        var today = ParisTime.today(now);
        var rows = RegistrationLines.sorted(deliveries.registrations(
                changes.stream().map(ChangeRow::registrationId).distinct().toList()));
        Predicate<RegistrationRow> upcoming = row -> !row.eveningDate().isBefore(today);
        var registeredLines = rows.stream().filter(upcoming)
                .filter(row -> registered.contains(row.id()) && !cancelled.contains(row.id()) && row.active())
                .map(row -> lines.active(row, byCoordinator.contains(row.id()), now))
                .toList();
        var cancelledLines = rows.stream().filter(upcoming)
                .filter(row -> cancelled.contains(row.id()) && !registered.contains(row.id()) && !row.active())
                .map(row -> lines.cancelled(row, byCoordinator.contains(row.id())))
                .toList();
        if (registeredLines.isEmpty() && cancelledLines.isEmpty()) {
            return new Skipped(AbandonReason.NOTHING_TO_SEND);
        }
        var contact = commons.coordinatorsContact();
        var rendered = renderer.render("confirmation", "mail.confirmation.subject", null, Map.of(
                "registered", registeredLines,
                "cancelled", cancelledLines,
                "contact", lines.contact(contact),
                "myRegistrations", commons.link("/mes-inscriptions")));
        return new Ready(new OutgoingMail(List.of(email.get()), MailCommons.replyTo(contact), rendered.subject(),
                rendered.text(), rendered.html()));
    }

    private static Set<UUID> ids(List<ChangeRow> changes, Change change) {
        return changes.stream().filter(row -> row.change() == change).map(ChangeRow::registrationId)
                .collect(Collectors.toSet());
    }
}
