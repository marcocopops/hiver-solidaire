package com.marcantoinemeunier.hiversolidaire.inscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegistrationChoiceTest {

    private static final String EVENING = "3f8e5a8e-8a8b-4c3e-9a55-1c7f2d1e0b11";

    @Test
    void parses_a_type_with_its_dish() {
        assertThat(RegistrationChoice.parse(EVENING, "PREPARE_AT_HOME.SIDES")).contains(
                new RegistrationChoice(UUID.fromString(EVENING), RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES));
    }

    @Test
    void parses_a_type_without_dish() {
        assertThat(RegistrationChoice.parse(EVENING, "BREAKFAST")).contains(
                new RegistrationChoice(UUID.fromString(EVENING), RegistrationType.BREAKFAST, null));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "null, BREAKFAST",
        EVENING + ", null",
        "not-a-uuid, BREAKFAST",
        EVENING + ", UNKNOWN",
        EVENING + ", BREAKFAST.SIDES",
        EVENING + ", PREPARE_AT_HOME",
        EVENING + ", PREPARE_AT_HOME.SIDES.MAIN_DISH",
        EVENING + ", //evil.example.org",
    })
    void rejects_anything_else(String evening, String choice) {
        assertThat(RegistrationChoice.parse(evening, choice)).isEmpty();
    }

    @Test
    void builds_a_local_path_carrying_the_choice() {
        var choice = RegistrationChoice.parse(EVENING, "PREPARE_AND_SHARE.MAIN_DISH").orElseThrow();

        assertThat(choice.appendTo("/inscription/confirmer"))
                .isEqualTo("/inscription/confirmer?soiree=" + EVENING + "&choix=PREPARE_AND_SHARE.MAIN_DISH");
    }
}
