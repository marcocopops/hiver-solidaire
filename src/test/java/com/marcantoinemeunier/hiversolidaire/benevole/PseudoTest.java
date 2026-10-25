package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pseudo public : règles de forme et suggestion « prénom + initiale du nom ». */
class PseudoTest {

    @ParameterizedTest
    @ValueSource(strings = {"MarieD", "maried", "Élodie", "Anne-Marie", "jean_pierre", "Zoé42", "42Zoé", "Łukasz",
        "Œdipe", "abc", "a2345678901234567890"})
    void accepts_latin_letters_with_accents_digits_hyphen_and_underscore(String pseudo) {
        assertThat(Pseudo.normalize(pseudo)).contains(pseudo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "a23456789012345678901", "<script>", "Marie D", "Marie.D", "O'Neil", "Marie@D",
        "-Marie", "_Marie", "Marie😀", "Маrie", "Ａnne", "Ｍarie", "ΜarieD", "Marie\u0000", "Ma\nrie", "Marie&amp;",
        "Zoe\u0301\u0301", "١٢٣abc"})
    void rejects_everything_else(String pseudo) {
        assertThat(Pseudo.normalize(pseudo)).isEmpty();
    }

    @Test
    void null_is_rejected() {
        assertThat(Pseudo.normalize(null)).isEmpty();
    }

    @Test
    void surrounding_spaces_are_removed_and_accents_composed() {
        // « e » suivi d'un accent aigu combinant devient « é » (NFC) : un seul pseudo pour deux saisies identiques.
        assertThat(Pseudo.normalize("  Ele\u0301onore ")).contains("Eléonore");
        assertThat(Pseudo.normalize("Ma\u0301rie")).contains("Márie");
    }

    @Test
    void the_uniqueness_key_ignores_case() {
        assertThat(Pseudo.key("MarieD")).isEqualTo(Pseudo.key("maried"));
        assertThat(Pseudo.key("Élodie")).isEqualTo(Pseudo.key("élodie"));
        assertThat(Pseudo.key("Elodie")).isNotEqualTo(Pseudo.key("Élodie"));
    }

    @ParameterizedTest
    @CsvSource({
        "Marie,          Dupont,           MarieD",
        "marie,          dupont,           marieD",
        "Anne-Marie,     Martin,           Anne-MarieM",
        "Anne Marie,     Martin,           AnneMarieM",
        "Élise,          d'Aubigné,        ÉliseD",
        "Jean-Baptiste,  Él,               Jean-BaptisteÉ",
        "Jo,             Bé,               JoB",
        "J,              B,                JB1",
        "Maximilienne-Alexandrine, Dupont, Maximilienne-AlexanD",
        "Νίκος,          Papadopoulos,     BenevoleP",
        "Anne,           '',               Anne",
    })
    void suggests_first_name_and_initial_of_last_name(String firstName, String lastName, String expected) {
        var suggestion = Pseudo.suggest(firstName, lastName);

        assertThat(suggestion).isEqualTo(expected);
        assertThat(Pseudo.normalize(suggestion)).as("la suggestion est toujours valide").contains(suggestion);
    }

    @Test
    void suggestion_ignores_missing_names() {
        assertThat(Pseudo.suggest(null, null)).isEqualTo("Benevole");
        assertThat(Pseudo.suggest("  ", "Dupont")).isEqualTo("BenevoleD");
    }

    @Test
    void variants_add_a_number_and_stay_within_20_characters() {
        assertThat(Pseudo.variant("MarieD", 2)).isEqualTo("MarieD2");
        assertThat(Pseudo.variant("a2345678901234567890", 2)).isEqualTo("a234567890123456789" + "2");
        assertThat(Pseudo.variant("a2345678901234567890", 12)).isEqualTo("a23456789012345678" + "12");
        assertThat(Pseudo.normalize(Pseudo.variant("Maximilienne-AlexanD", 3))).isPresent();
    }
}
