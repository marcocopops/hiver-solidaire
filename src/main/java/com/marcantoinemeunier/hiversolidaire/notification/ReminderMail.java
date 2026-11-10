package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.AbandonReason;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Kind;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rappel de la veille à 18 h (§6) : tout ce que le bénévole fait sur la carte du lendemain (ADR-0009), le
 * petit-déjeuner du matin (soirée de la veille) puis le dîner et la nuit du soir. Les inscriptions sont relues à
 * l'envoi : un poste annulé entre-temps n'y est plus, et sans poste, aucun e-mail. Pour le soir, la désinscription est
 * encore possible jusqu'à 23 h 59 ; pour le petit-déjeuner, la limite est passée et l'e-mail renvoie aux coordinateurs.
 */
@Component
class ReminderMail implements MailComposer {

    private final MailDeliveries deliveries;
    private final RegistrationLines lines;
    private final MailCommons commons;
    private final MailRenderer renderer;

    ReminderMail(MailDeliveries deliveries, RegistrationLines lines, MailCommons commons, MailRenderer renderer) {
        this.deliveries = deliveries;
        this.lines = lines;
        this.commons = commons;
        this.renderer = renderer;
    }

    @Override
    public Kind kind() {
        return Kind.REMINDER;
    }

    @Override
    public Composed compose(MailDelivery delivery, Instant now) {
        var email = deliveries.activeEmail(delivery.volunteerId());
        if (email.isEmpty()) {
            return new Skipped(AbandonReason.NO_RECIPIENT);
        }
        var day = delivery.referenceDate();
        var rows = RegistrationLines.sorted(deliveries.activeOnDay(delivery.volunteerId(), day));
        if (rows.isEmpty()) {
            return new Skipped(AbandonReason.NOTHING_TO_SEND);
        }
        var contact = commons.coordinatorsContact();
        var label = DayLabelFormatter.inline(day);
        var rendered = renderer.render("rappel", "mail.reminder.subject", new Object[] {label}, Map.of(
                "day", label,
                "lines", rows.stream().map(row -> lines.active(row, false, now)).toList(),
                "contact", lines.contact(contact),
                "myRegistrations", commons.link("/mes-inscriptions")));
        return new Ready(new OutgoingMail(List.of(email.get()), MailCommons.replyTo(contact), rendered.subject(),
                rendered.text(), rendered.html()));
    }
}
