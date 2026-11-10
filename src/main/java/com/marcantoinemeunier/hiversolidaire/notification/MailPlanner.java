package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.auth.CoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Création des e-mails planifiés à leur heure ({@link MailSchedule}) : le rappel de la carte du lendemain à 18 h, le
 * récap des coordinateurs à 20 h. Les lignes sont créées par {@code ON CONFLICT DO NOTHING} : appeler cette méthode à
 * chaque tour, ou après un redémarrage, ne double rien. Si l'appli était arrêtée à l'heure prévue, le rappel est
 * rattrapé jusqu'à 22 h et le récap jusqu'au lendemain 8 h ; ensuite, ils sont perdus.
 */
@Component
class MailPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(MailPlanner.class);

    /** Développement : un e-mail créé à la main garde au moins ce délai pour partir. */
    private static final Duration DEVELOPMENT_GRACE = Duration.ofHours(1);

    private final MailDeliveries deliveries;
    private final MailCommons commons;
    private final CoordinatorEmails coordinators;
    private final Clock clock;

    MailPlanner(MailDeliveries deliveries, MailCommons commons, CoordinatorEmails coordinators, Clock clock) {
        this.deliveries = deliveries;
        this.commons = commons;
        this.coordinators = coordinators;
        this.clock = clock;
    }

    @Transactional
    void planDue() {
        var now = clock.instant();
        MailSchedule.reminderDay(now).ifPresent(day -> planReminders(day,
                day.minusDays(1).atTime(MailSchedule.REMINDER_FROM).atZone(ParisTime.ZONE).toInstant(),
                MailSchedule.reminderNotAfter(day), now));
        MailSchedule.recapDay(now).ifPresent(day -> planRecap(day, MailSchedule.recapNotAfter(day), now));
    }

    /** Développement seulement : rappel de demain et récap d'aujourd'hui, tout de suite. */
    @Transactional
    void planNow() {
        var now = clock.instant();
        var today = ParisTime.today(now);
        var tomorrow = today.plusDays(1);
        planReminders(tomorrow, now, latest(MailSchedule.reminderNotAfter(tomorrow), now.plus(DEVELOPMENT_GRACE)), now);
        planRecap(today, latest(MailSchedule.recapNotAfter(today), now.plus(DEVELOPMENT_GRACE)), now);
    }

    /**
     * Un rappel par bénévole qui a une inscription montrée sur la carte {@code day}. Une inscription enregistrée après
     * 18 h n'en reçoit pas : sa confirmation part de toute façon.
     */
    private void planReminders(LocalDate day, Instant registeredBefore, Instant notAfter, Instant now) {
        var created = 0;
        for (var volunteerId : deliveries.volunteersToRemind(day, registeredBefore)) {
            if (deliveries.createReminder(volunteerId, day, now, notAfter)) {
                created++;
            }
        }
        if (created > 0) {
            LOG.info("{} reminder(s) created for {}", created, day);
        }
    }

    /**
     * Récap du jour {@code day}, s'il y a au moins un coordinateur et que la saison est ouverte : de l'ouverture des
     * inscriptions jusqu'à la dernière soirée (le récap de ce jour-là couvre encore son petit-déjeuner).
     */
    private void planRecap(LocalDate day, Instant notAfter, Instant now) {
        if (coordinators.all().isEmpty() || commons.season().filter(season -> open(season, day, now)).isEmpty()) {
            return;
        }
        if (deliveries.createRecap(day, now, notAfter)) {
            LOG.info("Daily recap created for {}", day);
        }
    }

    private static boolean open(SeasonSettings season, LocalDate day, Instant now) {
        var opensAt = season.registrationsOpenAt().atZone(ParisTime.ZONE).toInstant();
        return !now.isBefore(opensAt) && !day.isAfter(season.lastEvening());
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
