package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Règles appliquées à l'écriture d'une inscription : R1, R2, R3, R7 (R4 n'empêche rien). */
class RegistrationDecisionTest {

    private static final LocalDate EVENING_DATE = LocalDate.of(2027, 1, 12);

    private static final Season SEASON = new Season(
            LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 20), paris(LocalDateTime.of(2026, 12, 14, 0, 0)));

    private static final Instant DURING_SEASON = paris(LocalDateTime.of(2027, 1, 5, 12, 0));

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static Instant paris(LocalDateTime dateTime) {
        return dateTime.atZone(ParisTime.ZONE).toInstant();
    }

    private static Evening evening(Registration... registrations) {
        return new Evening(EVENING_DATE, false, Needs.defaults(), List.of(registrations));
    }

    private static RegistrationDecision decide(Evening evening, Registration candidate, Instant now) {
        return evening.decide(SEASON, candidate, now);
    }

    @Test
    void registration_is_accepted_when_every_covered_post_has_a_place() {
        var candidate = Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH);

        assertThat(decide(evening(), candidate, DURING_SEASON)).isEqualTo(RegistrationDecision.ACCEPTED);
    }

    @Test
    void r1_refuses_when_one_covered_post_is_full() {
        var evening = evening(
                Registration.by(BOB, RegistrationType.PICK_UP_AND_SHARE, null),
                Registration.by(CAROL, RegistrationType.PICK_UP_AND_SHARE, null));
        // Il reste le plat principal, mais plus aucune présence au dîner.
        var candidate = Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH);

        assertThat(decide(evening, candidate, DURING_SEASON)).isEqualTo(RegistrationDecision.FULL);
    }

    @Test
    void r1_refuses_a_third_night_volunteer() {
        var evening = evening(
                Registration.by(BOB, RegistrationType.NIGHT_AND_DINNER, null),
                Registration.by(CAROL, RegistrationType.NIGHT_AND_DINNER, null));

        assertThat(decide(evening, Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null), DURING_SEASON))
                .isEqualTo(RegistrationDecision.FULL);
    }

    @Test
    void r2_refuses_a_second_registration_of_the_same_volunteer_on_the_same_evening() {
        var evening = evening(Registration.by(ALICE, RegistrationType.BREAKFAST, null));

        assertThat(decide(evening, Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null), DURING_SEASON))
                .isEqualTo(RegistrationDecision.ALREADY_REGISTERED);
    }

    @Test
    void r2_does_not_concern_other_volunteers() {
        var evening = evening(Registration.by(BOB, RegistrationType.NIGHT_AND_DINNER, null));

        assertThat(decide(evening, Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null), DURING_SEASON))
                .isEqualTo(RegistrationDecision.ACCEPTED);
    }

    @Test
    void r3_refuses_a_dish_already_taken() {
        var evening = evening(Registration.by(BOB, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH));
        var candidate = Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH);

        assertThat(decide(evening, candidate, DURING_SEASON)).isEqualTo(RegistrationDecision.DISH_TAKEN);
    }

    @Test
    void r3_accepts_the_other_dish() {
        var evening = evening(Registration.by(BOB, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH));
        var candidate = Registration.by(ALICE, RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES);

        assertThat(decide(evening, candidate, DURING_SEASON)).isEqualTo(RegistrationDecision.ACCEPTED);
    }

    @Test
    void r4_accepts_a_home_preparation_without_pick_up_and_keeps_the_alert_computable() {
        var candidate = Registration.by(ALICE, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH);
        var evening = evening();

        assertThat(decide(evening, candidate, DURING_SEASON)).isEqualTo(RegistrationDecision.ACCEPTED);
        assertThat(evening(candidate).needsPickupAlert()).isTrue();
    }

    @Test
    void r7_refuses_before_opening() {
        var now = paris(LocalDateTime.of(2026, 12, 13, 23, 59));

        assertThat(decide(evening(), Registration.by(ALICE, RegistrationType.BREAKFAST, null), now))
                .isEqualTo(RegistrationDecision.NOT_OPEN_YET);
    }

    @Test
    void r7_accepts_on_the_evening_itself_until_midnight_paris_time() {
        var now = paris(LocalDateTime.of(2027, 1, 12, 23, 59));

        assertThat(decide(evening(), Registration.by(ALICE, RegistrationType.BREAKFAST, null), now))
                .isEqualTo(RegistrationDecision.ACCEPTED);
    }

    @Test
    void r7_refuses_a_past_evening() {
        var now = paris(LocalDateTime.of(2027, 1, 13, 0, 0));

        assertThat(decide(evening(), Registration.by(ALICE, RegistrationType.BREAKFAST, null), now))
                .isEqualTo(RegistrationDecision.PAST);
    }

    @Test
    void r7_refuses_a_closed_evening() {
        var closed = new Evening(EVENING_DATE, true, Needs.defaults(), List.of());

        assertThat(decide(closed, Registration.by(ALICE, RegistrationType.BREAKFAST, null), DURING_SEASON))
                .isEqualTo(RegistrationDecision.CLOSED);
    }

    @Test
    void r7_refuses_an_evening_outside_the_season() {
        var outside = new Evening(LocalDate.of(2027, 3, 21), false, Needs.defaults(), List.of());

        assertThat(decide(outside, Registration.by(ALICE, RegistrationType.BREAKFAST, null), DURING_SEASON))
                .isEqualTo(RegistrationDecision.OUT_OF_SEASON);
    }

    @Test
    void decision_needs_a_volunteer() {
        var anonymous = Registration.of(RegistrationType.BREAKFAST);

        assertThatIllegalArgumentException().isThrownBy(() -> decide(evening(), anonymous, DURING_SEASON));
    }
}
