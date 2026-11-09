package com.marcantoinemeunier.hiversolidaire.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marcantoinemeunier.hiversolidaire.config.CoordinatorsContact.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinatorsContactTest {

    @Test
    void a_missing_contact_gives_nothing() {
        assertThat(CoordinatorsContact.parse(null)).isEmpty();
        assertThat(CoordinatorsContact.parse("  ")).isEmpty();
    }

    @Test
    void a_french_number_keeps_its_display_and_gets_a_tel_link() {
        assertThat(CoordinatorsContact.parse(" 01 99 00 12 34 "))
                .contains(new CoordinatorsContact(Kind.PHONE, "01 99 00 12 34", "tel:0199001234"));
        assertThat(CoordinatorsContact.parse("01.99.00.12.34").orElseThrow().uri()).isEqualTo("tel:0199001234");
        assertThat(CoordinatorsContact.parse("06-12-34-56-78").orElseThrow().uri()).isEqualTo("tel:0612345678");
    }

    @Test
    void a_number_with_the_french_country_code_keeps_its_plus() {
        assertThat(CoordinatorsContact.parse("+33 1 99 00 12 34"))
                .contains(new CoordinatorsContact(Kind.PHONE, "+33 1 99 00 12 34", "tel:+33199001234"));
    }

    @Test
    void an_email_address_is_lowercased_and_gets_a_mailto_link() {
        assertThat(CoordinatorsContact.parse(" Coordination.Hiver+2027@Example.org "))
                .contains(new CoordinatorsContact(Kind.EMAIL, "coordination.hiver+2027@example.org",
                        "mailto:coordination.hiver+2027@example.org"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "12",
        "01 99 00 12",
        "01 99 00 12 34 56",
        "00 99 00 12 34",
        "+44 20 7946 0000",
        "+33 0 99 00 12 34",
        "appelez-nous",
        "<script>",
        "javascript:alert(1)",
        "tel:0199001234",
        "mailto:coordination@example.org",
        "coordination@",
        "@example.org",
        "coordination@localhost",
        "Coordination <coordination@example.org>",
        "a@example.org, b@example.org",
        // Une partie locale avec « ? », « & », « % » ou « = » ajouterait des en-têtes ou un encodage au lien mailto:.
        "a?bcc=autre@example.org",
        "a&cc=autre@example.org",
        "a%40b@example.org",
        "a=b@example.org",
    })
    void anything_else_is_refused(String value) {
        assertThatThrownBy(() -> CoordinatorsContact.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_address_longer_than_254_characters_is_refused() {
        var value = "a".repeat(60) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".org";
        assertThat(value.length()).isGreaterThan(254);
        assertThatThrownBy(() -> CoordinatorsContact.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_refusal_never_contains_the_value() {
        assertThatThrownBy(() -> CoordinatorsContact.parse("a?bcc=autre@example.org"))
                .message().doesNotContain("autre@example.org");
    }
}
