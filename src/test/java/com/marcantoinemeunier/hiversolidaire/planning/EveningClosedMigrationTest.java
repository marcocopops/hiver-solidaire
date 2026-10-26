package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La migration V9 supprime la colonne {@code evening.closed} sur une base de développement qui contient déjà des
 * inscriptions, actives et annulées, y compris sur une soirée fermée : aucune n'est touchée.
 */
class EveningClosedMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final LocalDate OPEN = LocalDate.of(2027, 1, 4);
    private static final LocalDate CLOSED = LocalDate.of(2027, 1, 5);
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void migrateToV8AndFill() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway("8").migrate();
        var alice = UUID.randomUUID();
        var bob = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, 'alice@example.org'), (?, 'bob@example.org')",
                alice, bob);
        jdbc.update("UPDATE evening SET closed = true WHERE evening_date = ?", CLOSED);
        register(alice, OPEN, "BREAKFAST", null, false);
        register(bob, OPEN, "PREPARE_AND_SHARE", "MAIN_DISH", true);
        register(alice, CLOSED, "NIGHT_AND_DINNER", null, false);
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

    private static void register(UUID volunteerId, LocalDate date, String type, String dishChoice,
            boolean cancelled) {
        jdbc.update("""
                INSERT INTO registration (id, evening_id, volunteer_id, type, dish_choice, cancelled_at)
                SELECT gen_random_uuid(), e.id, ?, ?, ?, CASE WHEN ? THEN now() END
                FROM evening e WHERE e.evening_date = ?
                """, volunteerId, type, dishChoice, cancelled, date);
    }

    @Test
    void the_closed_column_is_gone() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'evening' AND column_name = 'closed'
                """, Integer.class)).isZero();
    }

    @Test
    void registrations_are_untouched() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE cancelled_at IS NULL",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.cancelled_at IS NULL
                """, Integer.class, CLOSED)).isEqualTo(1);
    }

    @Test
    void every_evening_is_kept() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evening", Integer.class)).isEqualTo(76);
    }
}
