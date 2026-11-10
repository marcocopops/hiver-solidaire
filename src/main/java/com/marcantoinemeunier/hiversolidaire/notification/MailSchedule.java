package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Créneaux des e-mails planifiés, à l'heure de Paris, changements d'heure compris (cahier des charges §6, ADR-0012).
 * Java pur, sans Spring : la tâche planifiée lui passe l'heure de l'horloge injectée.
 * <ul>
 *   <li>rappel de la journée D (carte du planning, ADR-0009) : créé le jour D-1 de 18 h à 22 h. Si l'appli était
 *       arrêtée à 18 h, il est rattrapé jusqu'à 22 h, jamais après (on n'écrit pas la nuit, et le premier petit-déjeuner
 *       est à 7 h) ;</li>
 *   <li>récap du jour X : créé de X à 20 h jusqu'au lendemain 8 h ; un seul par jour.</li>
 * </ul>
 */
public final class MailSchedule {

    static final LocalTime REMINDER_FROM = LocalTime.of(18, 0);
    static final LocalTime REMINDER_UNTIL = LocalTime.of(22, 0);
    static final LocalTime RECAP_FROM = LocalTime.of(20, 0);
    static final LocalTime RECAP_UNTIL = LocalTime.of(8, 0);

    private MailSchedule() {
    }

    /** Journée dont le rappel est à créer maintenant (le lendemain), ou vide hors du créneau de 18 h à 22 h. */
    public static Optional<LocalDate> reminderDay(Instant now) {
        var local = LocalDateTime.ofInstant(now, ParisTime.ZONE);
        var time = local.toLocalTime();
        if (time.isBefore(REMINDER_FROM) || !time.isBefore(REMINDER_UNTIL)) {
            return Optional.empty();
        }
        return Optional.of(local.toLocalDate().plusDays(1));
    }

    /** Au-delà, le rappel de la journée {@code day} n'est plus envoyé : la veille à 22 h. */
    public static Instant reminderNotAfter(LocalDate day) {
        return at(day.minusDays(1), REMINDER_UNTIL);
    }

    /** Jour dont le récap est à créer maintenant, ou vide de 8 h à 20 h. */
    public static Optional<LocalDate> recapDay(Instant now) {
        var local = LocalDateTime.ofInstant(now, ParisTime.ZONE);
        var time = local.toLocalTime();
        if (!time.isBefore(RECAP_FROM)) {
            return Optional.of(local.toLocalDate());
        }
        if (time.isBefore(RECAP_UNTIL)) {
            return Optional.of(local.toLocalDate().minusDays(1));
        }
        return Optional.empty();
    }

    /** Au-delà, le récap du jour {@code day} n'est plus envoyé : le lendemain à 8 h. */
    public static Instant recapNotAfter(LocalDate day) {
        return at(day.plusDays(1), RECAP_UNTIL);
    }

    /** R7 : une soirée est passée à minuit le lendemain, heure de Paris ; ses e-mails ne partent plus. */
    public static Instant eveningPastAt(LocalDate eveningDate) {
        return at(eveningDate.plusDays(1), LocalTime.MIDNIGHT);
    }

    /** Horaire fixe du petit-déjeuner (§5) : 7 h en semaine, 8 h le week-end. */
    public static int breakfastHour(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY ? 8 : 7;
    }

    private static Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ParisTime.ZONE).toInstant();
    }
}
