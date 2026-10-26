package com.marcantoinemeunier.hiversolidaire.inscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegistrationChoiceTest {

    private static final String DAY = "2027-01-05";

    @Test
    void parses_a_type_with_its_dish() {
        assertThat(RegistrationChoice.parse(DAY, "PREPARE_AT_HOME.SIDES")).contains(
                new RegistrationChoice(LocalDate.parse(DAY), RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES));
    }

    @Test
    void parses_a_type_without_dish() {
        assertThat(RegistrationChoice.parse(DAY, "BREAKFAST")).contains(
                new RegistrationChoice(LocalDate.parse(DAY), RegistrationType.BREAKFAST, null));
    }

    @Test
    void breakfast_belongs_to_the_previous_evening_and_the_rest_to_the_same_evening() {
        assertThat(RegistrationChoice.parse(DAY, "BREAKFAST").orElseThrow().eveningDate())
                .isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(RegistrationChoice.parse(DAY, "NIGHT_AND_DINNER").orElseThrow().eveningDate())
                .isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void a_choice_can_be_built_from_its_evening() {
        assertThat(RegistrationChoice.onEvening(LocalDate.of(2027, 1, 4), RegistrationType.BREAKFAST, null))
                .isEqualTo(new RegistrationChoice(LocalDate.parse(DAY), RegistrationType.BREAKFAST, null));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "null, BREAKFAST",
        DAY + ", null",
        "not-a-date, BREAKFAST",
        "2027-02-30, BREAKFAST",
        DAY + ", UNKNOWN",
        DAY + ", BREAKFAST.SIDES",
        DAY + ", PREPARE_AT_HOME",
        DAY + ", PREPARE_AT_HOME.SIDES.MAIN_DISH",
        DAY + ", //evil.example.org",
    })
    void rejects_anything_else(String day, String choice) {
        assertThat(RegistrationChoice.parse(day, choice)).isEmpty();
    }

    @Test
    void builds_a_local_path_carrying_the_choice() {
        var choice = RegistrationChoice.parse(DAY, "PREPARE_AND_SHARE.MAIN_DISH").orElseThrow();

        assertThat(choice.appendTo("/inscription/confirmer"))
                .isEqualTo("/inscription/confirmer?jour=" + DAY + "&choix=PREPARE_AND_SHARE.MAIN_DISH");
    }
}
