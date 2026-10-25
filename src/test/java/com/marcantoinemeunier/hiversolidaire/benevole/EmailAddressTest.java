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
        "anne@@example.org", "anne@example..org",
        // L'adresse enregistrée doit être exactement celle qui reçoit le lien : pas de forme « Nom <adresse> »,
        // pas de liste de destinataires.
        "x<autre@example.net>@example.org", "anne@example.org,autre@example.net", "anne@example.org;b@example.net",
        "\"anne\"@example.org", "anne@exa_mple.org", "anne@-example.org"})
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

    @Test
    void masked_address_keeps_the_first_letter_and_the_domain() {
        assertThat(EmailAddress.mask("anne.martin@example.org")).isEqualTo("a•••@example.org");
        assertThat(EmailAddress.mask("a@example.org")).isEqualTo("a•••@example.org");
    }
}
