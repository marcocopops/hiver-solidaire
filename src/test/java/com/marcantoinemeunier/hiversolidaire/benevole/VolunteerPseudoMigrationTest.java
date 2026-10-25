package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La migration V7 passe sur une base de développement qui contient déjà des bénévoles : chaque profil complet
 * reçoit un pseudo « prénom + initiale », numéroté en cas de doublon, par ordre d'arrivée. En production, la table
 * est vide et la migration n'a rien à remplir.
 */
class VolunteerPseudoMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void migrateToV6AndFill() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway("6").migrate();
        volunteer("marie1@example.org", "Marie", "Dupont", "2026-11-01T10:00:00Z");
        volunteer("marie2@example.org", "Marie", "Durand", "2026-11-02T10:00:00Z");
        volunteer("marie3@example.org", "marie", "dumas", "2026-11-03T10:00:00Z");
        volunteer("anne@example.org", "Anne Marie", "d'Aubigné", "2026-11-04T10:00:00Z");
        volunteer("nikos@example.org", "Νίκος", "Papadopoulos", "2026-11-05T10:00:00Z");
        volunteer("jb@example.org", "J", "B", "2026-11-06T10:00:00Z");
        volunteer("long@example.org", "Maximilienne-Alexandrine", "Dupont", "2026-11-07T10:00:00Z");
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, 'sans-profil@example.org')", UUID.randomUUID());
        flyway(null).migrate();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void volunteer(String email, String firstName, String lastName, String createdAt) {
        jdbc.update("""
                INSERT INTO volunteer (id, email, first_name, last_name, phone, created_at, profile_completed_at)
                VALUES (?, ?, ?, ?, '0600000001', ?, ?)
                """, UUID.randomUUID(), email, firstName, lastName, Timestamp.from(Instant.parse(createdAt)),
                Timestamp.from(Instant.parse(createdAt)));
    }

    private static String pseudoOf(String email) {
        return jdbc.queryForObject("SELECT pseudo FROM volunteer WHERE email = ?", String.class, email);
    }

    @Test
    void first_name_and_initial_with_a_number_for_duplicates_by_arrival() {
        assertThat(pseudoOf("marie1@example.org")).isEqualTo("MarieD");
        assertThat(pseudoOf("marie2@example.org")).isEqualTo("MarieD2");
        assertThat(pseudoOf("marie3@example.org")).isEqualTo("marieD3");
    }

    @Test
    void same_rules_as_the_application_suggestion() {
        for (var email : new String[] {"anne@example.org", "nikos@example.org", "jb@example.org",
            "long@example.org"}) {
            var pseudo = pseudoOf(email);
            assertThat(Pseudo.normalize(pseudo)).as(email).contains(pseudo);
        }
        assertThat(pseudoOf("anne@example.org")).isEqualTo(Pseudo.suggest("Anne Marie", "d'Aubigné"));
        assertThat(pseudoOf("nikos@example.org")).isEqualTo(Pseudo.suggest("Νίκος", "Papadopoulos"));
        assertThat(pseudoOf("jb@example.org")).isEqualTo(Pseudo.suggest("J", "B"));
        assertThat(pseudoOf("long@example.org")).isEqualTo(Pseudo.suggest("Maximilienne-Alexandrine", "Dupont"));
    }

    @Test
    void volunteers_without_profile_get_no_pseudo_yet() {
        assertThat(pseudoOf("sans-profil@example.org")).isNull();
    }
}
