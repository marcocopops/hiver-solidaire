package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SeasonTest {

    private static final Instant OPENING = Instant.parse("2025-12-01T00:00:00Z");

    @Test
    void season_2025_2026_had_76_evenings() {
        // Du lundi 5 janvier au soir au dimanche 22 mars au matin : dernière soirée le samedi 21 mars.
        var season = new Season(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 3, 21), OPENING);

        assertThat(season.eveningDates())
                .hasSize(76)
                .startsWith(LocalDate.of(2026, 1, 5))
                .endsWith(LocalDate.of(2026, 3, 21));
    }

    @Test
    void single_evening_season_is_allowed() {
        var date = LocalDate.of(2027, 1, 4);

        assertThat(new Season(date, date, OPENING).eveningDates()).containsExactly(date);
    }

    @Test
    void last_evening_cannot_precede_first_evening() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Season(LocalDate.of(2027, 3, 20), LocalDate.of(2027, 1, 4), OPENING));
    }

    @Test
    void contains_checks_season_bounds() {
        var season = new Season(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 20), OPENING);

        assertThat(season.contains(LocalDate.of(2027, 1, 4))).isTrue();
        assertThat(season.contains(LocalDate.of(2027, 3, 20))).isTrue();
        assertThat(season.contains(LocalDate.of(2027, 1, 3))).isFalse();
        assertThat(season.contains(LocalDate.of(2027, 3, 21))).isFalse();
    }
}
