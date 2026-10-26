package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R7 Fenêtre : de l'ouverture à la fin de saison, jamais sur une soirée passée. */
class RegistrationWindowTest {

    private static final LocalDate EVENING_DATE = LocalDate.of(2027, 1, 12);

    private static final Season SEASON = new Season(
            LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 20), paris(LocalDateTime.of(2026, 12, 14, 0, 0)));

    private static Instant paris(LocalDateTime dateTime) {
        return dateTime.atZone(ParisTime.ZONE).toInstant();
    }

    private static Evening evening() {
        return new Evening(EVENING_DATE, Needs.defaults(), List.of());
    }

    @Test
    void registrations_are_refused_before_opening() {
        var now = paris(LocalDateTime.of(2026, 12, 13, 23, 59));

        assertThat(SEASON.acceptsRegistrations(evening(), now)).isFalse();
    }

    @Test
    void registrations_are_accepted_from_opening() {
        var now = paris(LocalDateTime.of(2026, 12, 14, 0, 0));

        assertThat(SEASON.acceptsRegistrations(evening(), now)).isTrue();
    }

    @Test
    void registrations_are_accepted_on_the_evening_itself() {
        var now = paris(LocalDateTime.of(2027, 1, 12, 23, 59));

        assertThat(SEASON.acceptsRegistrations(evening(), now)).isTrue();
    }

    @Test
    void past_evening_refuses_registrations_from_midnight_paris_time() {
        var now = paris(LocalDateTime.of(2027, 1, 13, 0, 0));

        assertThat(evening().isPast(ParisTime.today(now))).isTrue();
        assertThat(SEASON.acceptsRegistrations(evening(), now)).isFalse();
    }

    @Test
    void past_is_evaluated_in_paris_time_not_utc() {
        // 23 h 30 UTC le 12 janvier = 0 h 30 le 13 janvier à Paris (UTC+1 en hiver).
        var now = Instant.parse("2027-01-12T23:30:00Z");

        assertThat(SEASON.acceptsRegistrations(evening(), now)).isFalse();
    }

    @Test
    void evening_outside_the_season_refuses_registrations() {
        var outside = new Evening(LocalDate.of(2027, 3, 21), Needs.defaults(), List.of());
        var now = paris(LocalDateTime.of(2027, 1, 5, 12, 0));

        assertThat(SEASON.acceptsRegistrations(outside, now)).isFalse();
    }
}
