package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Formulaire de première connexion : validation et normalisation, données factices. */
class ProfileFormTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static ProfileForm form(String lastName, String firstName, String phone) {
        var form = new ProfileForm();
        form.setLastName(lastName);
        form.setFirstName(firstName);
        form.setPhone(phone);
        return form;
    }

    @Test
    void a_complete_form_is_valid_and_recontact_is_unchecked_by_default() {
        var form = form("Martin-Dupont", "Anne Marie", "06 00 00 00 01");

        assertThat(VALIDATOR.validate(form)).isEmpty();
        assertThat(form.isRecontactNextSeason()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0600000001", "06 00 00 00 01", "06.00.00.00.01", "+33 6 00 00 00 01", "0033600000001"})
    void french_phone_numbers_are_accepted_and_normalized(String phone) {
        var form = form("Martin", "Anne", phone);

        assertThat(VALIDATOR.validate(form)).isEmpty();
        assertThat(form.normalizedPhone()).isEqualTo("0600000001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "06 00 00 00", "12345", "06 00 00 00 01 02", "0000000000", "appelez-moi"})
    void invalid_phone_numbers_are_rejected(String phone) {
        assertThat(VALIDATOR.validate(form("Martin", "Anne", phone))).isNotEmpty();
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
