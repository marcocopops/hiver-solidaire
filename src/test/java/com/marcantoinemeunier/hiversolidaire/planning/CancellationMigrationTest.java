package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La migration V8 passe sur une base de développement qui contient déjà des bénévoles et des inscriptions : elles
 * restent actives, et R2 et R3 restent garanties. En production, les tables sont vides.
 */
class CancellationMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final LocalDate EVENING = LocalDate.of(2027, 1, 4);
    private static final UUID ALICE = UUID.randomUUID();
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void migrateToV7AndFill() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway("7").migrate();
        var bob = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, 'alice@example.org'), (?, 'bob@example.org')",
                ALICE, bob);
        register(ALICE, "PREPARE_AT_HOME", "MAIN_DISH");
        register(bob, "BREAKFAST", null);
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

    private static void register(UUID volunteerId, String type, String dishChoice) {
        jdbc.update("""
                INSERT INTO registration (id, evening_id, volunteer_id, type, dish_choice)
                SELECT gen_random_uuid(), e.id, ?, ?, ? FROM evening e WHERE e.evening_date = ?
                """, volunteerId, type, dishChoice, EVENING);
    }

    @Test
    void existing_registrations_stay_active() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE cancelled_at IS NULL", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM volunteer WHERE deleted_at IS NULL", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void existing_registrations_still_block_duplicates() {
        assertThatThrownBy(() -> register(ALICE, "NIGHT_AND_DINNER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        var carol = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, 'carol@example.org')", carol);
        assertThatThrownBy(() -> register(carol, "PREPARE_AND_SHARE", "MAIN_DISH"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
