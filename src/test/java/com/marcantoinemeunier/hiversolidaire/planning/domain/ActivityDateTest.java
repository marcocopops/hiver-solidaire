package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Jour de l'activité d'une inscription (ADR-0009) : le petit-déjeuner a lieu le lendemain matin de sa soirée. */
class ActivityDateTest {

    private static final LocalDate MONDAY = LocalDate.of(2027, 1, 4);
    private static final LocalDate TUESDAY = LocalDate.of(2027, 1, 5);

    @Test
    void breakfast_takes_place_the_morning_after_its_evening() {
        assertThat(RegistrationType.BREAKFAST.activityDate(MONDAY)).isEqualTo(TUESDAY);
        assertThat(RegistrationType.BREAKFAST.eveningDate(TUESDAY)).isEqualTo(MONDAY);
    }

    @Test
    void every_other_type_takes_place_on_the_evening_itself() {
        for (var type : RegistrationType.values()) {
            if (type != RegistrationType.BREAKFAST) {
                assertThat(type.activityDate(MONDAY)).as(type.name()).isEqualTo(MONDAY);
                assertThat(type.eveningDate(MONDAY)).as(type.name()).isEqualTo(MONDAY);
            }
        }
    }

    @Test
    void the_month_and_year_change_are_handled() {
        assertThat(RegistrationType.BREAKFAST.activityDate(LocalDate.of(2026, 12, 31)))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
