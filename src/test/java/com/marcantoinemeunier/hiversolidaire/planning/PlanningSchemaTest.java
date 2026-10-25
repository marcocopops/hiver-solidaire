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

/** Contraintes des migrations V2 et V4, et contenu de la saison d'exemple V3. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PlanningSchemaTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 1, 4);

    @Autowired
    JdbcTemplate jdbc;

    private UUID volunteer(String email) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, ?)", id, email);
        return id;
    }

    private void register(String type, String dishChoice) {
        register(volunteer(UUID.randomUUID() + "@example.org"), type, dishChoice);
    }

    private void register(UUID volunteerId, String type, String dishChoice) {
        jdbc.update("""
                INSERT INTO registration (id, evening_id, volunteer_id, type, dish_choice)
                SELECT gen_random_uuid(), e.id, ?, ?, ? FROM evening e WHERE e.evening_date = ?
                """, volunteerId, type, dishChoice, EVENING);
    }

    @Test
    void sample_season_is_flagged_and_has_76_evenings_and_default_needs() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM season WHERE sample", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evening", Integer.class)).isEqualTo(76);
        assertThat(jdbc.queryForObject(
                "SELECT minimum || '/' || maximum FROM season_need WHERE post = 'NIGHT'", String.class))
                .isEqualTo("1/2");
    }

    @Test
    void only_one_main_dish_per_evening() {
        register("PREPARE_AT_HOME", "MAIN_DISH");

        assertThatThrownBy(() -> register("PREPARE_AND_SHARE", "MAIN_DISH"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preparing_requires_a_dish_choice() {
        assertThatThrownBy(() -> register("PREPARE_AT_HOME", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void other_types_reject_a_dish_choice() {
        assertThatThrownBy(() -> register("BREAKFAST", "SIDES"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknown_type_is_rejected() {
        assertThatThrownBy(() -> register("SOMETHING_ELSE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void an_evening_date_is_unique_within_a_season() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO evening (id, season_id, evening_date)
                SELECT gen_random_uuid(), season_id, evening_date FROM evening WHERE evening_date = ?
                """, EVENING)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_volunteer_registers_only_once_per_evening() {
        var alice = volunteer("alice@example.org");
        register(alice, "BREAKFAST", null);

        assertThatThrownBy(() -> register(alice, "NIGHT_AND_DINNER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_registration_needs_an_existing_volunteer() {
        assertThatThrownBy(() -> register(UUID.randomUUID(), "BREAKFAST", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_registration_needs_a_volunteer() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO registration (id, evening_id, type)
                SELECT gen_random_uuid(), e.id, 'BREAKFAST' FROM evening e WHERE e.evening_date = ?
                """, EVENING)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void volunteer_email_is_unique() {
        volunteer("alice@example.org");

        assertThatThrownBy(() -> volunteer("alice@example.org")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void volunteer_email_is_stored_normalized() {
        assertThatThrownBy(() -> volunteer("Alice@Example.org")).isInstanceOf(DataIntegrityViolationException.class);
        // Chaque test tourne dans sa propre transaction : PostgreSQL l'abandonne après la première erreur.
    }

    @Test
    void volunteer_email_is_stored_without_surrounding_spaces() {
        assertThatThrownBy(() -> volunteer(" alice@example.org")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_completed_profile_requires_names_and_phone() {
        var alice = volunteer("alice@example.org");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE volunteer SET profile_completed_at = now(), last_name = 'Martin' WHERE id = ?", alice))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recontact_next_season_is_unchecked_by_default() {
        var alice = volunteer("alice@example.org");

        assertThat(jdbc.queryForObject("SELECT recontact_next_season FROM volunteer WHERE id = ?", Boolean.class,
                alice)).isFalse();
    }
}
