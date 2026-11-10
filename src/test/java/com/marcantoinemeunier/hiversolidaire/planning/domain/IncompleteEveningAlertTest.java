package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Alerte « soirée devenue incomplète » (§6) : une soirée des 7 prochains jours perd un minimum. Elle part par e-mail
 * tout de suite si le dîner de la soirée commence dans moins de 48 h ; sinon, elle n'apparaît que dans le récap (S5a).
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

    private static Instant paris(LocalDateTime local) {
        return local.atZone(ParisTime.ZONE).toInstant();
    }

    @Test
    void an_evening_whose_dinner_starts_within_48_hours_is_mailed_immediately() {
        var evening = LocalDate.of(2027, 1, 12);

        assertThat(IncompleteEveningAlert.immediate(evening, paris(LocalDateTime.of(2027, 1, 10, 19, 1)))).isTrue();
        assertThat(IncompleteEveningAlert.immediate(evening, paris(LocalDateTime.of(2027, 1, 12, 10, 0)))).isTrue();
        // Dîner commencé : la nuit et le petit-déjeuner restent à venir.
        assertThat(IncompleteEveningAlert.immediate(evening, paris(LocalDateTime.of(2027, 1, 12, 21, 0)))).isTrue();
    }

    @Test
    void an_evening_48_hours_or_more_ahead_waits_for_the_recap() {
        var evening = LocalDate.of(2027, 1, 12);

        assertThat(IncompleteEveningAlert.immediate(evening, paris(LocalDateTime.of(2027, 1, 10, 19, 0)))).isFalse();
        assertThat(IncompleteEveningAlert.immediate(evening, paris(LocalDateTime.of(2027, 1, 9, 20, 0)))).isFalse();
    }

    @Test
    void the_48_hours_follow_daylight_saving_time() {
        // Dîner du lundi 29 mars 2027 à 19 h (heure d'été, 17 h UTC) : 48 h avant, c'est le samedi 27 à 18 h (heure
        // d'hiver, 17 h UTC), et non à 19 h.
        var evening = LocalDate.of(2027, 3, 29);

        assertThat(IncompleteEveningAlert.immediate(evening, Instant.parse("2027-03-27T17:00:00Z"))).isFalse();
        assertThat(IncompleteEveningAlert.immediate(evening, Instant.parse("2027-03-27T17:01:00Z"))).isTrue();
    }
}
