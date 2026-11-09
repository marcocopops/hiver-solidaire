package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DayStatus;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Needs;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Season;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Jeu de données de développement ({@code scripts/seed-dev.sql}, lot 1) : exécuté sur une base fraîchement migrée, il
 * remplit les 4 premières semaines de la saison sans enfreindre R1 à R4 ni R7, et ne double rien quand on le relance.
 * Les règles sont vérifiées en rejouant chaque inscription et chaque annulation, dans l'ordre chronologique, par le
 * domaine de l'appli ({@link Evening#decide}) : le contrôle ne dépend pas de la requête de contrôle du script.
 */
class SeedDevScriptTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Path SEED = Path.of("scripts/seed-dev.sql");
    private static final Path RESET = Path.of("scripts/seed-dev-reset.sql");

    private static final int SEED_VOLUNTEERS = 21;
    private static final int ACTIVE_REGISTRATIONS = 59;
    private static final int CANCELLED_REGISTRATIONS = 4;
    private static final int FILLED_EVENINGS_OF_FIRST_FOUR_WEEKS = 17;

    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void start() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    private static void freshDatabase(String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        var flyway = configuration.load();
        flyway.clean();
        flyway.migrate();
    }

    /** Comme {@code psql --single-transaction} dans {@code seed-dev.sh} : tout ou rien. */
    private static void run(Path script) {
        String sql;
        try {
            sql = Files.readString(script);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> jdbc.execute(sql));
    }

    private static void seed() {
        run(SEED);
    }

    private static int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static Map<String, Integer> counts() {
        var counts = new HashMap<String, Integer>();
        counts.put("volunteers", count("SELECT count(*) FROM volunteer"));
        counts.put("active", count("SELECT count(*) FROM registration WHERE cancelled_at IS NULL"));
        counts.put("cancelled", count("SELECT count(*) FROM registration WHERE cancelled_at IS NOT NULL"));
        counts.put("alerts", count("SELECT count(*) FROM coordinator_alert"));
        return counts;
    }

    @Test
    void fills_about_sixty_percent_of_the_first_four_weeks_with_fictitious_volunteers() {
        freshDatabase(null);
        seed();

        assertThat(count("SELECT count(*) FROM volunteer")).isEqualTo(SEED_VOLUNTEERS);
        assertThat(count("SELECT count(*) FROM volunteer WHERE email IS NULL AND profile_completed_at IS NOT NULL"))
                .as("un bénévole inscrit par téléphone").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM volunteer WHERE email IS NOT NULL AND email NOT LIKE '%@example.org'"))
                .as("adresses factices seulement").isZero();
        assertThat(count("SELECT count(*) FROM volunteer WHERE phone !~ '^\\+33(6399800[0-9]{2}|199000[0-9]{3})$'"))
                .as("numéros de la plage réservée à la fiction").isZero();
        assertThat(count("SELECT count(*) FROM volunteer WHERE reviewed_at IS NULL")).as("nouveaux non vus")
                .isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT pseudo FROM volunteer", String.class))
                .allMatch(pseudo -> pseudo.matches("^[A-ZÀ-Ý][a-zà-ÿ]+[A-Z]$"));

        assertThat(count("SELECT count(*) FROM registration WHERE cancelled_at IS NULL"))
                .isEqualTo(ACTIVE_REGISTRATIONS);
        assertThat(count("SELECT count(*) FROM registration WHERE cancelled_at IS NOT NULL"))
                .isEqualTo(CANCELLED_REGISTRATIONS);
        assertThat(count("""
                SELECT count(DISTINCT r.evening_id) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.cancelled_at IS NULL AND e.evening_date < (SELECT first_evening + 28 FROM season)
                """)).isEqualTo(FILLED_EVENINGS_OF_FIRST_FOUR_WEEKS);
        assertThat(count("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date >= (SELECT first_evening + 28 FROM season)
                """)).as("rien au-delà des 4 premières semaines").isZero();

        assertThat(count("SELECT count(*) FROM coordinator_alert WHERE handled_at IS NULL")).isEqualTo(1);
    }

    @Test
    void every_registration_and_cancellation_is_accepted_by_the_domain_rules_in_chronological_order() {
        freshDatabase(null);
        seed();

        var season = jdbc.queryForObject("SELECT first_evening, last_evening, registrations_open_at FROM season",
                (rs, i) -> new Season(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class),
                        rs.getTimestamp(3).toInstant()));
        record Event(Instant at, boolean creation, LocalDate evening, Registration registration) {
        }
        var events = new ArrayList<Event>();
        jdbc.query("""
                SELECT e.evening_date, r.type, r.dish_choice, r.volunteer_id, r.created_at, r.cancelled_at
                FROM registration r JOIN evening e ON e.id = r.evening_id
                """, rs -> {
            var registration = Registration.by(rs.getObject(4, UUID.class),
                    RegistrationType.valueOf(rs.getString(2)),
                    rs.getString(3) == null ? null : DishChoice.valueOf(rs.getString(3)));
            var evening = rs.getObject(1, LocalDate.class);
            events.add(new Event(rs.getTimestamp(5).toInstant(), true, evening, registration));
            if (rs.getTimestamp(6) != null) {
                var cancelledAt = rs.getTimestamp(6).toInstant();
                assertThat(cancelledAt).isAfter(rs.getTimestamp(5).toInstant());
                assertThat(cancelledAt).as("annulée avant la fin de la soirée")
                        .isBefore(evening.plusDays(1).atStartOfDay(PARIS).toInstant());
                events.add(new Event(cancelledAt, false, evening, registration));
            }
        });
        events.sort(Comparator.comparing(Event::at).thenComparing(event -> !event.creation()));

        var needs = Needs.defaults();
        var byEvening = new HashMap<LocalDate, List<Registration>>();
        for (var event : events) {
            var current = byEvening.computeIfAbsent(event.evening(), date -> new ArrayList<>());
            if (event.creation()) {
                var decision = new Evening(event.evening(), needs, current).decide(season, event.registration(),
                        event.at());
                assertThat(decision).as("%s le %s", event.registration(), event.evening())
                        .isEqualTo(RegistrationDecision.ACCEPTED);
                current.add(event.registration());
            } else {
                assertThat(current.remove(event.registration())).isTrue();
            }
        }

        var evenings = season.eveningDates().stream()
                .map(date -> new Evening(date, needs, byEvening.getOrDefault(date, List.of())))
                .toList();
        assertThat(evenings).as("R4 : pas de récupération sans repas préparé chez soi").allSatisfy(evening ->
                assertThat(evening.registrations().stream().filter(r -> r.type() == RegistrationType.PICK_UP_AND_SHARE)
                        .count()).isLessThanOrEqualTo(evening.registrations().stream()
                        .filter(r -> r.type() == RegistrationType.PREPARE_AT_HOME).count()));
        assertThat(evenings.stream().filter(Evening::needsPickupAlert)).as("préparation chez soi sans récupération")
                .hasSize(1);
        assertThat(evenings.stream().filter(evening -> evening.registrations().stream()
                .anyMatch(r -> r.type() == RegistrationType.PICK_UP_AND_SHARE)))
                .as("préparation chez soi avec sa récupération").hasSize(1);

        var firstFourWeeks = Day.group(evenings).stream()
                .filter(day -> day.date().isBefore(season.firstEvening().plusDays(28)))
                .toList();
        assertThat(firstFourWeeks.stream().filter(day -> day.status() == DayStatus.COMPLETE)).hasSize(7);
        var incompleteWithSomeone = firstFourWeeks.stream()
                .filter(day -> day.status() == DayStatus.INCOMPLETE)
                .filter(day -> day.posts().stream().anyMatch(post -> day.missing(post) == 0))
                .toList();
        assertThat(incompleteWithSomeone).as("journées incomplètes à divers degrés").hasSizeGreaterThan(5);
    }

    @Test
    void running_it_twice_or_after_a_reset_gives_the_same_data() {
        freshDatabase(null);
        seed();
        var first = counts();

        seed();
        assertThat(counts()).isEqualTo(first);

        run(RESET);
        seed();
        assertThat(counts()).isEqualTo(first);
    }

    @Test
    void reset_empties_registrations_alerts_and_audit_log_but_keeps_volunteers_it_did_not_create() {
        freshDatabase(null);
        var own = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, 'a-la-main@example.org')", own);
        seed();
        jdbc.update("""
                INSERT INTO audit_log (id, occurred_at, coordinator_id, action) VALUES (?, now(), ?, 'SEASON_UPDATED')
                """, UUID.randomUUID(), UUID.randomUUID());

        run(RESET);

        assertThat(count("SELECT count(*) FROM registration")).isZero();
        assertThat(count("SELECT count(*) FROM coordinator_alert")).isZero();
        assertThat(count("SELECT count(*) FROM audit_log")).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM volunteer", UUID.class)).containsExactly(own);
    }

    @Test
    void a_second_run_skips_evenings_that_already_have_registrations_of_other_volunteers() {
        freshDatabase(null);
        var own = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, first_name, last_name, phone, pseudo, profile_completed_at)
                VALUES (?, 'a-la-main@example.org', 'Test', 'Manuel', '+33639980099', 'TestM', now())
                """, own);
        jdbc.update("""
                INSERT INTO registration (id, evening_id, type, dish_choice, volunteer_id, created_at)
                SELECT gen_random_uuid(), e.id, 'PREPARE_AND_SHARE', 'MAIN_DISH', ?, s.registrations_open_at
                FROM evening e JOIN season s ON s.id = e.season_id WHERE e.evening_date = s.first_evening
                """, own);

        seed();

        assertThat(count("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                JOIN season s ON s.id = e.season_id WHERE e.evening_date = s.first_evening
                """)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM registration WHERE cancelled_at IS NULL"))
                .isEqualTo(ACTIVE_REGISTRATIONS - 4 + 1);
    }

    @Test
    void the_one_post_per_volunteer_migration_passes_on_a_seeded_database() {
        // Base de développement remplie par le lot 1 avant le lot 3 : la V14 (ADR-0011) doit passer sans rien perdre.
        freshDatabase("13");
        seed();
        var before = counts();

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(counts()).isEqualTo(before);
        assertThat(count("""
                SELECT count(*) FROM pg_indexes WHERE tablename = 'registration'
                    AND indexname = 'registration_one_active_post_per_volunteer_and_evening'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM pg_indexes WHERE tablename = 'registration'
                    AND indexname = 'registration_one_active_per_volunteer_and_evening'
                """)).isZero();
    }

    @Test
    void a_second_run_accepts_posts_cumulated_by_hand() {
        // R2 porte sur le poste (lot 3) : un bénévole inscrit à la main sur la nuit et le plat d'une même soirée ne fait
        // pas échouer le contrôle final.
        freshDatabase(null);
        seed();
        var own = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, first_name, last_name, phone, pseudo, profile_completed_at)
                VALUES (?, 'cumul@example.org', 'Test', 'Cumul', '+33639980098', 'TestC', now())
                """, own);
        for (var type : List.of("NIGHT_AND_DINNER", "BREAKFAST")) {
            jdbc.update("""
                    INSERT INTO registration (id, evening_id, type, volunteer_id, created_at)
                    SELECT gen_random_uuid(), e.id, ?, ?, s.registrations_open_at
                    FROM evening e JOIN season s ON s.id = e.season_id WHERE e.evening_date = s.first_evening + 40
                    """, type, own);
        }

        seed();

        assertThat(count("SELECT count(*) FROM registration WHERE cancelled_at IS NULL"))
                .isEqualTo(ACTIVE_REGISTRATIONS + 2);
    }

    @Test
    void refuses_a_database_without_the_expected_schema() {
        freshDatabase("11");

        assertThatThrownBy(SeedDevScriptTest::seed).hasMessageContaining("schéma");
        assertThatThrownBy(() -> run(RESET)).hasMessageContaining("schéma");
    }

    @Test
    void refuses_a_database_with_more_than_one_season() {
        freshDatabase(null);
        jdbc.update("""
                INSERT INTO season (id, first_evening, last_evening, registrations_open_at)
                VALUES (gen_random_uuid(), DATE '2028-01-03', DATE '2028-03-19', TIMESTAMPTZ '2027-12-13 00:00:00 Europe/Paris')
                """);

        assertThatThrownBy(SeedDevScriptTest::seed).hasMessageContaining("une seule saison");
        assertThat(count("SELECT count(*) FROM volunteer")).isZero();
    }

    @Test
    void the_final_check_rejects_data_that_breaks_the_rules() {
        freshDatabase(null);
        var ids = new ArrayList<UUID>();
        for (int i = 1; i <= 3; i++) {
            var id = UUID.randomUUID();
            ids.add(id);
            jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, ?)", id, "surplus" + i + "@example.org");
        }
        for (var id : ids) {
            jdbc.update("""
                    INSERT INTO registration (id, evening_id, type, volunteer_id, created_at)
                    SELECT gen_random_uuid(), e.id, 'PICK_UP_AND_SHARE', ?, s.registrations_open_at
                    FROM evening e JOIN season s ON s.id = e.season_id WHERE e.evening_date = s.first_evening + 40
                    """, id);
        }

        assertThatThrownBy(SeedDevScriptTest::seed).hasMessageContaining("R1");
        assertThat(count("SELECT count(*) FROM volunteer")).as("tout est annulé").isEqualTo(3);
    }

    @Test
    void the_final_check_rejects_a_pickup_cumulated_with_another_dinner_post() {
        freshDatabase(null);
        var own = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, 'recup-et-nuit@example.org')", own);
        for (var type : List.of("NIGHT_AND_DINNER", "PICK_UP_AND_SHARE")) {
            jdbc.update("""
                    INSERT INTO registration (id, evening_id, type, volunteer_id, created_at)
                    SELECT gen_random_uuid(), e.id, ?, ?, s.registrations_open_at
                    FROM evening e JOIN season s ON s.id = e.season_id WHERE e.evening_date = s.first_evening + 40
                    """, type, own);
        }

        assertThatThrownBy(SeedDevScriptTest::seed).hasMessageContaining("R2");
        assertThat(count("SELECT count(*) FROM volunteer")).as("tout est annulé").isEqualTo(1);
    }
}
