package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * R6 Désinscription : en autonomie jusqu'à la veille de la soirée à 23 h 59, heure de Paris. L'activité est le soir J
 * pour tous les types, petit-déjeuner du matin J+1 compris.
 */
class CancellationWindowTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 1, 12);

    private static Instant paris(LocalDateTime dateTime) {
        return dateTime.atZone(ParisTime.ZONE).toInstant();
    }

    @Test
    void cancellation_is_allowed_days_before() {
        assertThat(Cancellation.allowed(EVENING, paris(LocalDateTime.of(2027, 1, 5, 10, 0)))).isTrue();
    }

    @Test
    void cancellation_is_allowed_the_day_before_at_23_59() {
        assertThat(Cancellation.allowed(EVENING, paris(LocalDateTime.of(2027, 1, 11, 23, 59, 59)))).isTrue();
    }

    @Test
    void cancellation_is_refused_from_midnight_on_the_evening_itself() {
        assertThat(Cancellation.allowed(EVENING, paris(LocalDateTime.of(2027, 1, 12, 0, 0)))).isFalse();
        assertThat(Cancellation.allowed(EVENING, paris(LocalDateTime.of(2027, 1, 12, 18, 0)))).isFalse();
    }

    @Test
    void cancellation_is_refused_on_a_past_evening() {
        assertThat(Cancellation.allowed(EVENING, paris(LocalDateTime.of(2027, 1, 13, 7, 0)))).isFalse();
    }

    @Test
    void deadline_is_evaluated_in_paris_time_not_utc() {
        // 23 h 30 UTC le 11 janvier = 0 h 30 le 12 janvier à Paris (UTC+1 en hiver) : trop tard.
        assertThat(Cancellation.allowed(EVENING, Instant.parse("2027-01-11T23:30:00Z"))).isFalse();
        // 22 h 30 UTC le 11 janvier = 23 h 30 à Paris : encore possible.
        assertThat(Cancellation.allowed(EVENING, Instant.parse("2027-01-11T22:30:00Z"))).isTrue();
    }

    @Test
    void deadline_holds_when_summer_time_starts_the_night_before() {
        // Passage à l'heure d'été dans la nuit du 27 au 28 mars 2027 (2 h → 3 h) ; soirée du 29 mars.
        var evening = LocalDate.of(2027, 3, 29);

        assertThat(Cancellation.allowed(evening, paris(LocalDateTime.of(2027, 3, 28, 23, 59)))).isTrue();
        assertThat(Cancellation.allowed(evening, Instant.parse("2027-03-28T21:59:00Z"))).isTrue();
        assertThat(Cancellation.allowed(evening, Instant.parse("2027-03-28T22:00:00Z"))).isFalse();
    }

    @Test
    void deadline_holds_on_the_night_summer_time_starts() {
        // Soirée du 28 mars 2027 : la veille au soir, Paris est encore à UTC+1.
        var evening = LocalDate.of(2027, 3, 28);

        assertThat(Cancellation.allowed(evening, Instant.parse("2027-03-27T22:59:00Z"))).isTrue();
        assertThat(Cancellation.allowed(evening, Instant.parse("2027-03-27T23:00:00Z"))).isFalse();
    }

    @Test
    void deadline_holds_when_winter_time_starts() {
        // Retour à l'heure d'hiver dans la nuit du 24 au 25 octobre 2026 (3 h → 2 h) ; soirée du 26 octobre.
        var evening = LocalDate.of(2026, 10, 26);

        assertThat(Cancellation.allowed(evening, Instant.parse("2026-10-25T22:59:00Z"))).isTrue();
        assertThat(Cancellation.allowed(evening, Instant.parse("2026-10-25T23:00:00Z"))).isFalse();
    }

    @Test
    void deadline_is_the_day_before_at_23_59_paris_time() {
        assertThat(Cancellation.deadline(EVENING)).isEqualTo(LocalDateTime.of(2027, 1, 11, 23, 59));
    }
}
