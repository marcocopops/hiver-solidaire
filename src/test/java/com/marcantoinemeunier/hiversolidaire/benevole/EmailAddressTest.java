package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @Test
    void address_is_trimmed_and_lower_cased() {
        assertThat(EmailAddress.normalize("  Anne.Martin+hiver@Example.ORG ")).contains("anne.martin+hiver@example.org");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "anne", "anne@", "@example.org", "anne@example", "an ne@example.org",
        "anne@@example.org", "anne@example..org"})
    void malformed_addresses_are_rejected(String input) {
        assertThat(EmailAddress.normalize(input)).isEmpty();
    }

    @Test
    void null_is_rejected() {
        assertThat(EmailAddress.normalize(null)).isEmpty();
    }

    @Test
    void addresses_longer_than_254_characters_are_rejected() {
        var tooLong = "a".repeat(64) + "@" + "b".repeat(186) + ".org";

        assertThat(tooLong).hasSize(255);
        assertThat(EmailAddress.normalize(tooLong)).isEmpty();
    }
}
