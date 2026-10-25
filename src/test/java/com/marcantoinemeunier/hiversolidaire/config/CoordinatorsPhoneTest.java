package com.marcantoinemeunier.hiversolidaire.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CoordinatorsPhoneTest {

    @Test
    void a_missing_phone_gives_nothing() {
        assertThat(CoordinatorsPhone.parse(null)).isEmpty();
        assertThat(CoordinatorsPhone.parse("  ")).isEmpty();
    }

    @Test
    void a_french_number_keeps_its_display_and_gets_a_tel_link() {
        assertThat(CoordinatorsPhone.parse(" 01 99 00 12 34 "))
                .contains(new CoordinatorsPhone("01 99 00 12 34", "tel:0199001234"));
    }

    @Test
    void an_international_number_keeps_its_plus() {
        assertThat(CoordinatorsPhone.parse("+33 1 99 00 12 34").orElseThrow().telUri()).isEqualTo("tel:+33199001234");
    }

    @Test
    void anything_else_stops_the_application() {
        assertThatThrownBy(() -> CoordinatorsPhone.parse("<script>")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CoordinatorsPhone.parse("javascript:alert(1)"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CoordinatorsPhone.parse("12")).isInstanceOf(IllegalStateException.class);
    }
}
