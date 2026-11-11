package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Domaines réservés aux tests et à la documentation (RFC 2606, RFC 6761) : l'appli n'y envoie jamais rien en
 * production. Les adresses « permises » ci-dessous sont factices et non routables (littéral IP de documentation,
 * RFC 5737) ou n'existent que dans ce test.
 */
class ReservedDomainsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "benevole@example.org", "benevole@example.com", "benevole@example.net",
        "Benevole@EXAMPLE.ORG", "benevole@mail.example.org", "benevole@a.b.example.com",
        "benevole@example.org.", "benevole@hiver.test", "benevole@test", "benevole@foo.example",
        "benevole@deleted.invalid", "benevole@localhost", "benevole@app.localhost", "  benevole@example.org  "
    })
    void reserved_domains_are_recognised(String email) {
        assertThat(ReservedDomains.isReserved(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "benevole@[192.0.2.1]", "benevole@example.org.fr", "benevole@notexample.org", "benevole@example.organic",
        "benevole@testing.fr", "benevole@my-localhost.fr", "benevole@invalid.fr", "example.org@hiver-solidaire.fr"
    })
    void other_domains_are_not(String email) {
        assertThat(ReservedDomains.isReserved(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "pas-une-adresse"})
    void an_address_without_domain_is_treated_as_reserved(String email) {
        // Rien à envoyer à une adresse sans domaine : refus par prudence.
        assertThat(ReservedDomains.isReserved(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"benevole@example.org", "benevole@[192.0.2.1]"})
    void the_guard_lets_everything_through_when_reserved_domains_are_allowed(String email) {
        assertThat(new RecipientGuard(true).allows(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"benevole@example.org", "benevole@hiver.test"})
    void the_guard_blocks_reserved_domains_by_default(String email) {
        var guard = new RecipientGuard(false);

        assertThat(guard.allows(email)).isFalse();
        assertThat(guard.allows("benevole@[192.0.2.1]")).isTrue();
    }
}
