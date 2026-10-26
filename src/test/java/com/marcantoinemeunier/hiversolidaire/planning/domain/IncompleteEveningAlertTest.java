package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Alerte « soirée devenue incomplète » (§6) : une soirée des 7 prochains jours perd un minimum. La condition est
 * calculée ici ; l'e-mail aux coordinateurs arrive en S5.
 */
class IncompleteEveningAlertTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 1, 10);

    private final UUID alice = UUID.randomUUID();

    private static Evening evening(LocalDate date, Registration... registrations) {
        return new Evening(date, Needs.defaults(), List.of(registrations));
    }

    /** Soirée complète, dont Alice assure le petit-déjeuner. */
    private Evening complete(LocalDate date) {
        return evening(date,
                Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                Registration.by(UUID.randomUUID(), RegistrationType.NIGHT_AND_DINNER, null),
                Registration.by(alice, RegistrationType.BREAKFAST, null));
    }

    private boolean triggered(Evening before) {
        return IncompleteEveningAlert.triggeredBy(before, before.without(alice), TODAY);
    }

    @Test
    void losing_a_minimum_within_seven_days_triggers_the_alert() {
        var before = complete(TODAY.plusDays(3));

        assertThat(before.lostMinimums(before.without(alice))).containsExactly(Post.BREAKFAST);
        assertThat(triggered(before)).isTrue();
    }

    @Test
    void the_window_includes_the_seventh_day() {
        assertThat(triggered(complete(TODAY.plusDays(7)))).isTrue();
    }

    @Test
    void the_window_includes_today() {
        assertThat(triggered(complete(TODAY))).isTrue();
    }

    @Test
    void an_evening_eight_days_ahead_does_not_trigger_the_alert() {
        assertThat(triggered(complete(TODAY.plusDays(8)))).isFalse();
    }

    @Test
    void a_past_evening_does_not_trigger_the_alert() {
        assertThat(triggered(complete(TODAY.minusDays(1)))).isFalse();
    }

    @Test
    void a_post_still_at_its_minimum_does_not_trigger_the_alert() {
        // Deux bénévoles de nuit pour un minimum de 1 : le départ de l'un laisse le minimum couvert.
        var before = evening(TODAY.plusDays(2),
                Registration.by(UUID.randomUUID(), RegistrationType.NIGHT_AND_DINNER, null),
                Registration.by(alice, RegistrationType.NIGHT_AND_DINNER, null));

        assertThat(before.lostMinimums(before.without(alice))).isEmpty();
        assertThat(triggered(before)).isFalse();
    }

    @Test
    void a_partial_evening_that_loses_another_minimum_triggers_the_alert() {
        // Accompagnements déjà manquants ; le départ d'Alice fait perdre le petit-déjeuner.
        var before = evening(TODAY.plusDays(2),
                Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                Registration.by(alice, RegistrationType.BREAKFAST, null));

        assertThat(triggered(before)).isTrue();
    }

    @Test
    void a_post_already_missing_does_not_trigger_the_alert_again() {
        // Une seule présence au dîner sur deux : elle manquait déjà, la perdre ne fait perdre aucun minimum.
        var before = evening(TODAY.plusDays(2),
                Registration.by(alice, RegistrationType.PICK_UP_AND_SHARE, null));

        assertThat(triggered(before)).isFalse();
    }
}
