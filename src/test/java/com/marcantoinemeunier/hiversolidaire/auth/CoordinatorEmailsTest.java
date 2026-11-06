package com.marcantoinemeunier.hiversolidaire.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Lecture de {@code APP_COORDINATOR_EMAILS} (ADR-0010). Adresses factices. */
class CoordinatorEmailsTest {

    @Test
    void an_empty_list_has_no_coordinator() {
        assertThat(new CoordinatorEmails("").contains("a@example.org")).isFalse();
        assertThat(new CoordinatorEmails("  ").contains("a@example.org")).isFalse();
        assertThat(new CoordinatorEmails(null).contains("a@example.org")).isFalse();
    }

    @Test
    void addresses_are_separated_by_commas_and_normalized_like_at_sign_in() {
        var coordinators = new CoordinatorEmails(" Anne@Example.org ,bob@example.org,, ");

        assertThat(coordinators.contains("anne@example.org")).isTrue();
        assertThat(coordinators.contains("bob@example.org")).isTrue();
        assertThat(coordinators.contains("carole@example.org")).isFalse();
    }

    @Test
    void only_exact_addresses_match() {
        var coordinators = new CoordinatorEmails("anne@example.org");

        assertThat(coordinators.contains("anne@example.org.evil.example")).isFalse();
        assertThat(coordinators.contains("xanne@example.org")).isFalse();
    }

    @Test
    void an_invalid_entry_prevents_startup_without_echoing_it() {
        assertThatThrownBy(() -> new CoordinatorEmails("anne@example.org, not-an-address"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-an-address");
    }
}
