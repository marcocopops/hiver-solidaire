package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Saison présentée sur l'accueil : l'hiver porte l'année de sa fin, même s'il commence en décembre. */
class HomeSeasonTest {

    @Test
    void a_winter_starting_in_december_is_named_after_its_last_year() {
        var season = HomeController.HomeSeason.of(settings(LocalDate.of(2026, 12, 14), LocalDate.of(2027, 3, 20)));

        assertThat(season.winter()).isEqualTo("2027");
    }

    @Test
    void a_winter_within_one_year_is_named_after_that_year() {
        var season = HomeController.HomeSeason.of(settings(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 20)));

        assertThat(season.winter()).isEqualTo("2027");
    }

    private static SeasonSettings settings(LocalDate firstEvening, LocalDate lastEvening) {
        return new SeasonSettings(UUID.randomUUID(), firstEvening, lastEvening, LocalDateTime.of(2026, 12, 14, 9, 0),
                false, List.of(), null, null);
    }
}
