package com.marcantoinemeunier.hiversolidaire.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Repli de configuration du contact des coordinateurs : {@code APP_COORDINATORS_CONTACT}, sinon l'ancienne
 * {@code APP_COORDINATORS_PHONE}, lue le temps de mettre à jour le {@code .env} du serveur, avec un avertissement qui
 * ne contient jamais la valeur.
 */
@ExtendWith(OutputCaptureExtension.class)
class CoordinatorsContactFallbackTest {

    private static final String DEPRECATION = "APP_COORDINATORS_PHONE is deprecated";

    @Test
    void the_new_variable_comes_first(CapturedOutput output) {
        assertThat(CoordinatorsContactAdvice.fallback("coordination@example.org", "01 99 00 12 34"))
                .isEqualTo(CoordinatorsContact.parse("coordination@example.org").orElseThrow());
        assertThat(output).doesNotContain(DEPRECATION);
    }

    @Test
    void the_old_variable_is_still_read_with_a_warning_that_hides_its_value(CapturedOutput output) {
        assertThat(CoordinatorsContactAdvice.fallback("", "01 99 00 56 78"))
                .isEqualTo(CoordinatorsContact.parse("01 99 00 56 78").orElseThrow());
        assertThat(output).contains(DEPRECATION).contains("APP_COORDINATORS_CONTACT")
                .doesNotContain("01 99 00 56 78").doesNotContain("0199005678");
    }

    @Test
    void without_any_variable_there_is_no_fallback(CapturedOutput output) {
        assertThat(CoordinatorsContactAdvice.fallback(" ", "")).isNull();
        assertThat(CoordinatorsContactAdvice.fallback(null, null)).isNull();
        assertThat(output).doesNotContain(DEPRECATION);
    }

    @Test
    void an_invalid_value_stops_the_application_without_showing_it() {
        assertThatThrownBy(() -> CoordinatorsContactAdvice.fallback("appelez-nous", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("app.coordinators.contact")
                .message().doesNotContain("appelez-nous");
        assertThatThrownBy(() -> CoordinatorsContactAdvice.fallback("", "appelez-nous"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("app.coordinators.phone")
                .message().doesNotContain("appelez-nous");
    }
}
