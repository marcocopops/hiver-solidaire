package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migration V14 (ADR-0011) : R2 garantie en base par poste, et non plus par soirée. Un bénévole cumule des postes
 * distincts ; il ne prend pas deux fois le même (le plat, la récupération, la nuit ou le petit-déjeuner). Les règles qui
 * croisent deux postes (« Récupérer » seulement avec le petit-déjeuner, R4) restent sous le verrou de la soirée.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OnePostPerVolunteerSchemaTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 3, 10);

    @Autowired
    JdbcTemplate jdbc;

    private UUID volunteer() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, ?)", id, id + "@example.org");
        return id;
    }

    private UUID register(UUID volunteerId, String type, String dishChoice) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO registration (id, evening_id, volunteer_id, type, dish_choice)
                SELECT ?, e.id, ?, ?, ? FROM evening e WHERE e.evening_date = ?
                """, id, volunteerId, type, dishChoice, EVENING);
        return id;
    }

    private int active(UUID volunteerId) {
        return jdbc.queryForObject("SELECT count(*) FROM registration WHERE volunteer_id = ? AND cancelled_at IS NULL",
                Integer.class, volunteerId);
    }

    @Test
    void a_volunteer_holds_distinct_posts_on_the_same_evening() {
        var alice = volunteer();

        register(alice, "PREPARE_AND_SHARE", "MAIN_DISH");
        register(alice, "PREPARE_AT_HOME", "SIDES");
        register(alice, "NIGHT_AND_DINNER", null);
        register(alice, "BREAKFAST", null);

        assertThat(active(alice)).isEqualTo(4);
    }

    @Test
    void the_same_night_twice_is_refused() {
        var alice = volunteer();
        register(alice, "NIGHT_AND_DINNER", null);

        assertThatThrownBy(() -> register(alice, "NIGHT_AND_DINNER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_same_pickup_twice_is_refused() {
        var alice = volunteer();
        register(alice, "PICK_UP_AND_SHARE", null);

        assertThatThrownBy(() -> register(alice, "PICK_UP_AND_SHARE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_same_dish_prepared_at_home_and_shared_is_one_post() {
        var alice = volunteer();
        register(alice, "PREPARE_AT_HOME", "SIDES");

        assertThatThrownBy(() -> register(alice, "PREPARE_AND_SHARE", "SIDES"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_cancelled_registration_frees_its_post_for_the_same_volunteer() {
        var alice = volunteer();
        var night = register(alice, "NIGHT_AND_DINNER", null);
        jdbc.update("UPDATE registration SET cancelled_at = now() WHERE id = ?", night);

        register(alice, "NIGHT_AND_DINNER", null);

        assertThat(active(alice)).isEqualTo(1);
    }

    @Test
    void the_index_of_one_registration_per_evening_is_replaced() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE tablename = 'registration'
                    AND indexname = 'registration_one_active_per_volunteer_and_evening'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE tablename = 'registration'
                    AND indexname = 'registration_one_active_post_per_volunteer_and_evening'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void a_pickup_without_meal_alert_can_be_recorded() {
        jdbc.update("""
                INSERT INTO coordinator_alert (id, kind, evening_id, created_at)
                SELECT gen_random_uuid(), 'PICKUP_WITHOUT_MEAL', e.id, now() FROM evening e WHERE e.evening_date = ?
                """, EVENING);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM coordinator_alert WHERE kind = 'PICKUP_WITHOUT_MEAL'",
                Integer.class)).isEqualTo(1);
    }
}
