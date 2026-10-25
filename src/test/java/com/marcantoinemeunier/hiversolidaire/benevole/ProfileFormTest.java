package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Formulaire de première connexion : validation et normalisation, données factices. */
class ProfileFormTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static ProfileForm form(String lastName, String firstName, String countryCode, String phone) {
        var form = new ProfileForm();
        form.setLastName(lastName);
        form.setFirstName(firstName);
        form.setPhoneCountryCode(countryCode);
        form.setPhone(phone);
        return form;
    }

    private static ProfileForm form(String lastName, String firstName, String phone) {
        return form(lastName, firstName, "+33", phone);
    }

    @Test
    void a_complete_form_is_valid_and_recontact_is_unchecked_by_default() {
        var form = form("Martin-Dupont", "Anne Marie", "06 00 00 00 01");

        assertThat(VALIDATOR.validate(form)).isEmpty();
        assertThat(form.isRecontactNextSeason()).isFalse();
    }

    @Test
    void the_country_code_defaults_to_france() {
        assertThat(new ProfileForm().getPhoneCountryCode()).isEqualTo("+33");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0600000001", "06 00 00 00 01", "06.00.00.00.01", "06-00-00-00-01", "6 00 00 00 01"})
    void french_numbers_are_accepted_with_or_without_the_leading_zero(String phone) {
        var form = form("Martin", "Anne", phone);

        assertThat(VALIDATOR.validate(form)).isEmpty();
        assertThat(form.normalizedPhone()).isEqualTo("+33600000001");
    }

    @ParameterizedTest
    @CsvSource({
        // Indicatif saisi, numéro national saisi, numéro international enregistré (numéros fictifs).
        "+32,  0470 00 00 01,  +32470000001",
        "+41,  079 000 00 01,  +41790000001",
        "+44,  07700 900001,   +447700900001",
        "+1,   555 010 0001,   +15550100001",
        "+39,  06 0000 0001,   +390600000001",
        "0032, 470 00 00 01,   +32470000001",
        "32,   470 00 00 01,   +32470000001",
        "+ 33, 06 00 00 00 01, +33600000001",
    })
    void foreign_numbers_are_accepted_and_stored_in_international_format(String countryCode, String phone,
            String expected) {
        var form = form("Martin", "Anne", countryCode, phone);

        assertThat(VALIDATOR.validate(form)).isEmpty();
        assertThat(form.normalizedPhone()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "06 00 00 00", "12345", "06 00 00 00 01 02", "0000000000", "appelez-moi",
        "+33 6 00 00 00 01", "06 00 00 00 0x"})
    void invalid_french_numbers_are_rejected(String phone) {
        assertThat(VALIDATOR.validate(form("Martin", "Anne", phone))).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "'',     0470000001",
        "+0,     0470000001",
        "+1234,  0470000001",
        "abc,    0470000001",
        "+32,    123",
        "+32,    1234567890123456",
    })
    void invalid_country_codes_or_foreign_numbers_are_rejected(String countryCode, String phone) {
        assertThat(VALIDATOR.validate(form("Martin", "Anne", countryCode, phone))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "<script>", "Anne123", "-Anne", "Anne; DROP TABLE"})
    void names_accept_only_letters_and_name_punctuation(String name) {
        assertThat(VALIDATOR.validate(form(name, "Anne", "0600000001"))).isNotEmpty();
        assertThat(VALIDATOR.validate(form("Martin", name, "0600000001"))).isNotEmpty();
    }

    @Test
    void names_accept_accents_apostrophes_and_hyphens() {
        assertThat(VALIDATOR.validate(form("N’Diaye-Lefèvre", "Élise d'Aubigné", "0600000001"))).isEmpty();
    }

    @Test
    void names_longer_than_100_characters_are_rejected() {
        assertThat(VALIDATOR.validate(form("A".repeat(101), "Anne", "0600000001"))).isNotEmpty();
    }

    @Test
    void names_are_trimmed_and_inner_spaces_collapsed() {
        var form = form("  Martin  ", " Anne   Marie ", "0600000001");

        assertThat(form.normalizedLastName()).isEqualTo("Martin");
        assertThat(form.normalizedFirstName()).isEqualTo("Anne Marie");
    }
}
