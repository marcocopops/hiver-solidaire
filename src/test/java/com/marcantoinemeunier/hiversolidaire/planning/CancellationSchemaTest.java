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

/** Migration V8 : R2 et R3 ne portent que sur les inscriptions actives ; un compte supprimé est anonyme. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CancellationSchemaTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 1, 4);

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

    private void cancel(UUID registrationId) {
        jdbc.update("UPDATE registration SET cancelled_at = now() WHERE id = ?", registrationId);
    }

    @Test
    void after_cancellation_another_volunteer_takes_the_freed_dish() {
        cancel(register(volunteer(), "PREPARE_AT_HOME", "MAIN_DISH"));

        register(volunteer(), "PREPARE_AND_SHARE", "MAIN_DISH");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE dish_choice = 'MAIN_DISH'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void after_cancellation_the_volunteer_registers_again_on_the_same_evening() {
        var alice = volunteer();
        cancel(register(alice, "BREAKFAST", null));

        register(alice, "NIGHT_AND_DINNER", null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE volunteer_id = ?", Integer.class,
                alice)).isEqualTo(2);
    }

    @Test
    void two_active_registrations_of_the_same_volunteer_are_still_refused() {
        var alice = volunteer();
        register(alice, "BREAKFAST", null);

        assertThatThrownBy(() -> register(alice, "NIGHT_AND_DINNER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void two_active_main_dishes_are_still_refused() {
        register(volunteer(), "PREPARE_AT_HOME", "MAIN_DISH");

        assertThatThrownBy(() -> register(volunteer(), "PREPARE_AND_SHARE", "MAIN_DISH"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reactivating_a_cancelled_registration_cannot_create_a_duplicate() {
        var alice = volunteer();
        var cancelled = register(alice, "BREAKFAST", null);
        cancel(cancelled);
        register(alice, "NIGHT_AND_DINNER", null);

        assertThatThrownBy(() -> jdbc.update("UPDATE registration SET cancelled_at = NULL WHERE id = ?", cancelled))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void only_a_deleted_account_may_have_no_email() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, NULL)", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_deleted_account_keeps_no_personal_data() {
        var alice = volunteer();
        jdbc.update("UPDATE volunteer SET pseudo = 'AnneM', phone = '+33600000001' WHERE id = ?", alice);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE volunteer SET deleted_at = now(), email = NULL WHERE id = ?", alice))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_deleted_account_cannot_keep_its_email() {
        var alice = volunteer();

        assertThatThrownBy(() -> jdbc.update("UPDATE volunteer SET deleted_at = now() WHERE id = ?", alice))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void an_anonymous_deleted_account_keeps_its_past_registrations() {
        var alice = volunteer();
        register(alice, "BREAKFAST", null);

        jdbc.update("UPDATE volunteer SET deleted_at = now(), email = NULL WHERE id = ?", alice);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE volunteer_id = ?", Integer.class,
                alice)).isEqualTo(1);
    }

    @Test
    void coordinator_alert_kind_is_checked() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO coordinator_alert (id, kind, evening_id, created_at)
                SELECT gen_random_uuid(), 'SOMETHING_ELSE', e.id, now() FROM evening e WHERE e.evening_date = ?
                """, EVENING)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
