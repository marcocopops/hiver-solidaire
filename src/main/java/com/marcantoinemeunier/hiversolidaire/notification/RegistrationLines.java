package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.config.CoordinatorsContact;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.notification.MailDeliveries.RegistrationRow;
import com.marcantoinemeunier.hiversolidaire.planning.ActivityLabel;
import com.marcantoinemeunier.hiversolidaire.planning.DayLabelFormatter;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Cancellation;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Lignes d'inscription des e-mails aux bénévoles (confirmation, rappel) : ce que fait le bénévole, quand (horaires fixes
 * du §5), et comment se désinscrire. Le lien de désinscription ouvre la page de confirmation existante (GET sans effet),
 * jamais une action ; après la limite de R6, l'e-mail renvoie vers les coordinateurs.
 */
@Component
class RegistrationLines {

    /**
     * @param activity « Dîner du lun. 4 janv. »
     * @param option ce que le bénévole propose (« Passer la nuit et partager le dîner »)
     * @param schedule horaires fixes du §5
     * @param note « Enregistré (ou annulé) par un coordinateur. », ou {@code null}
     * @param cancelText date limite de R6, ou consigne de prévenir les coordinateurs ; {@code null} pour une
     *     désinscription
     * @param cancelLink lien absolu vers la page de confirmation de la désinscription, ou {@code null}
     */
    record Line(String activity, String option, String schedule, String note, String cancelText, String cancelLink) {
    }

    private final MailRenderer renderer;
    private final MailCommons commons;

    RegistrationLines(MailRenderer renderer, MailCommons commons) {
        this.renderer = renderer;
        this.commons = commons;
    }

    /** Dans l'ordre des jours d'activité, puis des postes de la journée. */
    static List<RegistrationRow> sorted(List<RegistrationRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparing((RegistrationRow row) -> row.type().activityDate(row.eveningDate()))
                        .thenComparingInt(row -> Day.POSTS.indexOf(Registration.of(row.type(), row.dishChoice())
                                .ownPost())))
                .toList();
    }

    /** Inscription active : horaires et désinscription. */
    Line active(RegistrationRow row, boolean byCoordinator, Instant now) {
        String cancelText;
        String cancelLink = null;
        if (Cancellation.allowed(row.eveningDate(), now)) {
            cancelText = renderer.message("mail.cancel.until",
                    DayLabelFormatter.deadline(Cancellation.deadline(row.eveningDate())));
            cancelLink = commons.link(RegistrationChoice.cancellationPath(row.eveningDate(), row.type(),
                    row.dishChoice()));
        } else {
            cancelText = renderer.message("mail.cancel.tooLate");
        }
        return new Line(activity(row), option(row), schedule(row),
                byCoordinator ? renderer.message("mail.byCoordinator") : null, cancelText, cancelLink);
    }

    /** Inscription annulée : ce qui ne tient plus, sans horaires. */
    Line cancelled(RegistrationRow row, boolean byCoordinator) {
        return new Line(activity(row), option(row), null,
                byCoordinator ? renderer.message("mail.byCoordinator.cancelled") : null, null, null);
    }

    /** Phrase de contact des coordinateurs, d'après les réglages de la saison (R6). */
    String contact(Optional<CoordinatorsContact> contact) {
        return contact.map(c -> renderer.message("mail.contact." + c.kind().name(), c.display()))
                .orElseGet(() -> renderer.message("mail.contact.none"));
    }

    private String activity(RegistrationRow row) {
        var label = ActivityLabel.of(row.type(), row.eveningDate());
        return renderer.message(label.key(), label.date());
    }

    private String option(RegistrationRow row) {
        return renderer.message("registration.option." + RegistrationChoice.choiceParam(row.type(), row.dishChoice()));
    }

    private String schedule(RegistrationRow row) {
        return renderer.message("mail.schedule." + row.type().name(),
                MailSchedule.breakfastHour(row.type().activityDate(row.eveningDate())));
    }
}
