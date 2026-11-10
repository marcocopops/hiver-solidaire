package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Créneaux des e-mails planifiés, à l'heure de Paris (cahier des charges §6) : rappel la veille de 18 h à 22 h, récap de
 * 20 h au lendemain 8 h, changements d'heure compris (28 mars et 31 octobre 2027).
 */
class MailScheduleTest {

    private static Instant paris(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ParisTime.ZONE).toInstant();
    }

    @Test
    void the_reminder_of_tomorrow_opens_at_six_pm() {
        assertThat(MailSchedule.reminderDay(paris(2027, 1, 10, 17, 59))).isEmpty();
        assertThat(MailSchedule.reminderDay(paris(2027, 1, 10, 18, 0))).contains(LocalDate.of(2027, 1, 11));
    }

    @Test
    void a_missed_reminder_is_caught_up_until_ten_pm_and_never_after() {
        assertThat(MailSchedule.reminderDay(paris(2027, 1, 10, 21, 59))).contains(LocalDate.of(2027, 1, 11));
        assertThat(MailSchedule.reminderDay(paris(2027, 1, 10, 22, 0))).isEmpty();
        assertThat(MailSchedule.reminderDay(paris(2027, 1, 11, 7, 0))).isEmpty();
    }

    @Test
    void the_reminder_expires_at_ten_pm_the_day_before() {
        assertThat(MailSchedule.reminderNotAfter(LocalDate.of(2027, 1, 11))).isEqualTo(paris(2027, 1, 10, 22, 0));
    }

    @Test
    void the_reminder_follows_daylight_saving_time() {
        // Passage à l'heure d'été le dimanche 28 mars 2027 à 2 h : 18 h vaut 17 h UTC la veille, 16 h UTC ce jour-là.
        assertThat(MailSchedule.reminderDay(Instant.parse("2027-03-27T17:00:00Z"))).contains(LocalDate.of(2027, 3, 28));
        assertThat(MailSchedule.reminderDay(Instant.parse("2027-03-28T15:59:00Z"))).isEmpty();
        assertThat(MailSchedule.reminderDay(Instant.parse("2027-03-28T16:00:00Z"))).contains(LocalDate.of(2027, 3, 29));
        assertThat(MailSchedule.reminderNotAfter(LocalDate.of(2027, 3, 29))).isEqualTo(Instant.parse("2027-03-28T20:00:00Z"));
        // Retour à l'heure d'hiver le dimanche 31 octobre 2027.
        assertThat(MailSchedule.reminderDay(Instant.parse("2027-10-31T16:59:00Z"))).isEmpty();
        assertThat(MailSchedule.reminderDay(Instant.parse("2027-10-31T17:00:00Z"))).contains(LocalDate.of(2027, 11, 1));
    }

    @Test
    void the_recap_of_the_day_opens_at_eight_pm() {
        assertThat(MailSchedule.recapDay(paris(2027, 1, 10, 19, 59))).isEmpty();
        assertThat(MailSchedule.recapDay(paris(2027, 1, 10, 20, 0))).contains(LocalDate.of(2027, 1, 10));
        assertThat(MailSchedule.recapDay(paris(2027, 1, 10, 23, 59))).contains(LocalDate.of(2027, 1, 10));
    }

    @Test
    void a_missed_recap_is_caught_up_until_eight_am_the_next_day() {
        assertThat(MailSchedule.recapDay(paris(2027, 1, 11, 7, 59))).contains(LocalDate.of(2027, 1, 10));
        assertThat(MailSchedule.recapDay(paris(2027, 1, 11, 8, 0))).isEmpty();
        assertThat(MailSchedule.recapNotAfter(LocalDate.of(2027, 1, 10))).isEqualTo(paris(2027, 1, 11, 8, 0));
    }

    @Test
    void the_recap_follows_daylight_saving_time() {
        // La nuit du changement d'heure : 8 h vaut 7 h UTC en hiver, 6 h UTC en été.
        assertThat(MailSchedule.recapNotAfter(LocalDate.of(2027, 3, 27))).isEqualTo(Instant.parse("2027-03-28T06:00:00Z"));
        assertThat(MailSchedule.recapNotAfter(LocalDate.of(2027, 10, 30))).isEqualTo(Instant.parse("2027-10-31T07:00:00Z"));
        assertThat(MailSchedule.recapDay(Instant.parse("2027-03-28T05:59:00Z"))).contains(LocalDate.of(2027, 3, 27));
        assertThat(MailSchedule.recapDay(Instant.parse("2027-03-28T06:00:00Z"))).isEmpty();
    }

    @Test
    void an_alert_expires_when_its_evening_is_past() {
        assertThat(MailSchedule.eveningPastAt(LocalDate.of(2027, 1, 10))).isEqualTo(paris(2027, 1, 11, 0, 0));
    }

    @Test
    void breakfast_is_at_seven_on_weekdays_and_eight_at_weekends() {
        assertThat(MailSchedule.breakfastHour(LocalDate.of(2027, 1, 8))).isEqualTo(7); // vendredi
        assertThat(MailSchedule.breakfastHour(LocalDate.of(2027, 1, 9))).isEqualTo(8); // samedi
        assertThat(MailSchedule.breakfastHour(LocalDate.of(2027, 1, 10))).isEqualTo(8); // dimanche
        assertThat(MailSchedule.breakfastHour(LocalDate.of(2027, 1, 11))).isEqualTo(7); // lundi
    }
}
