package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Contraintes de la migration V2 et contenu de la saison d'exemple V3. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PlanningSchemaTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 1, 4);

    @Autowired
    JdbcTemplate jdbc;

    private void register(String type, String dishChoice) {
        jdbc.update("""
                INSERT INTO registration (id, evening_id, type, dish_choice)
                SELECT gen_random_uuid(), e.id, ?, ? FROM evening e WHERE e.evening_date = ?
                """, type, dishChoice, EVENING);
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
}
