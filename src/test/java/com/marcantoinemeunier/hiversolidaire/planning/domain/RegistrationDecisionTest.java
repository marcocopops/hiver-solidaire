package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Règles appliquées à l'écriture d'une inscription : R1, R2 (un poste par bénévole et par soirée, ADR-0011), R3, R4
 * (une récupération seulement pour un repas préparé chez soi qui n'a pas la sienne) et R7.
 */
class RegistrationDecisionTest {

    private static final LocalDate EVENING_DATE = LocalDate.of(2027, 1, 12);

    private static final Season SEASON = new Season(
            LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 20), paris(LocalDateTime.of(2026, 12, 14, 0, 0)));

    private static final Instant DURING_SEASON = paris(LocalDateTime.of(2027, 1, 5, 12, 0));

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID DAN = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private static Instant paris(LocalDateTime dateTime) {
        return dateTime.atZone(ParisTime.ZONE).toInstant();
    }

    private static Evening evening(Registration... registrations) {
        return new Evening(EVENING_DATE, Needs.defaults(), List.of(registrations));
    }

    private static RegistrationDecision decide(Evening evening, Registration candidate, Instant now) {
        return evening.decide(SEASON, candidate, now);
    }

    /** Présente les candidats l'un après l'autre : chacun est décidé sur la soirée qui contient ceux déjà acceptés. */
    private static List<RegistrationDecision> inSequence(Needs needs, Registration... candidates) {
        var accepted = new ArrayList<Registration>();
        var decisions = new ArrayList<RegistrationDecision>();
        for (var candidate : candidates) {
            var decision = decide(new Evening(EVENING_DATE, needs, accepted), candidate, DURING_SEASON);
            decisions.add(decision);
            if (decision.accepted()) {
                accepted.add(candidate);
            }
        }
        return decisions;
    }

    private static List<RegistrationDecision> inSequence(Registration... candidates) {
        return inSequence(Needs.defaults(), candidates);
    }

    private static Registration pickup(UUID volunteer) {
        return Registration.by(volunteer, RegistrationType.PICK_UP_AND_SHARE, null);
    }

    private static Registration atHome(UUID volunteer, DishChoice dish) {
        return Registration.by(volunteer, RegistrationType.PREPARE_AT_HOME, dish);
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
    void r2_lets_a_volunteer_cumulate_the_night_the_main_dish_the_sides_and_breakfast() {
        assertThat(inSequence(
                Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null),
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                Registration.by(ALICE, RegistrationType.BREAKFAST, null)))
                .containsOnly(RegistrationDecision.ACCEPTED);
    }

    @Test
    void r2_lets_a_volunteer_prepare_one_dish_at_home_and_share_the_other() {
        assertThat(inSequence(
                atHome(ALICE, DishChoice.MAIN_DISH),
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null)))
                .containsOnly(RegistrationDecision.ACCEPTED);
    }

    @Test
    void r2_refuses_the_same_night_twice_even_with_a_night_place_left() {
        assertThat(inSequence(
                Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null),
                Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null)))
                .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ALREADY_REGISTERED);
    }

    @Test
    void r2_refuses_the_same_breakfast_twice() {
        assertThat(inSequence(
                Registration.by(ALICE, RegistrationType.BREAKFAST, null),
                Registration.by(ALICE, RegistrationType.BREAKFAST, null)))
                .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ALREADY_REGISTERED);
    }

    @Test
    void r2_refuses_the_same_dish_twice_before_r3_says_it_is_taken() {
        assertThat(inSequence(
                atHome(ALICE, DishChoice.MAIN_DISH),
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH)))
                .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ALREADY_REGISTERED);
    }

    @Test
    void r2_refuses_two_pickups_by_the_same_volunteer() {
        assertThat(inSequence(
                atHome(BOB, DishChoice.MAIN_DISH),
                atHome(CAROL, DishChoice.SIDES),
                pickup(ALICE),
                pickup(ALICE)))
                .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ACCEPTED,
                        RegistrationDecision.ACCEPTED, RegistrationDecision.ALREADY_REGISTERED);
    }

    @Test
    void r2_a_pickup_does_not_cumulate_with_another_post_of_the_same_dinner() {
        for (var other : List.of(
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                atHome(ALICE, DishChoice.SIDES),
                Registration.by(ALICE, RegistrationType.NIGHT_AND_DINNER, null))) {
            assertThat(inSequence(atHome(BOB, DishChoice.MAIN_DISH), other, pickup(ALICE))).as("après " + other)
                    .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ACCEPTED,
                            RegistrationDecision.NOT_CUMULABLE);
            assertThat(inSequence(atHome(BOB, DishChoice.MAIN_DISH), pickup(ALICE), other)).as("avant " + other)
                    .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ACCEPTED,
                            RegistrationDecision.NOT_CUMULABLE);
        }
    }

    @Test
    void r2_a_pickup_cumulates_with_breakfast() {
        var breakfast = Registration.by(ALICE, RegistrationType.BREAKFAST, null);

        assertThat(inSequence(atHome(BOB, DishChoice.MAIN_DISH), pickup(ALICE), breakfast))
                .containsOnly(RegistrationDecision.ACCEPTED);
        assertThat(inSequence(atHome(BOB, DishChoice.MAIN_DISH), breakfast, pickup(ALICE)))
                .containsOnly(RegistrationDecision.ACCEPTED);
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
    void r4_refuses_a_pickup_when_no_meal_is_prepared_at_home() {
        assertThat(decide(evening(), pickup(ALICE), DURING_SEASON)).isEqualTo(RegistrationDecision.NOTHING_TO_PICK_UP);
        var sharedOnly = evening(
                Registration.by(BOB, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                Registration.by(CAROL, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES));

        assertThat(decide(sharedOnly, pickup(ALICE), DURING_SEASON))
                .isEqualTo(RegistrationDecision.NOTHING_TO_PICK_UP);
    }

    @Test
    void r4_one_home_preparation_opens_one_pickup_and_a_second_one_opens_a_second() {
        assertThat(inSequence(
                atHome(BOB, DishChoice.MAIN_DISH),
                pickup(ALICE),
                pickup(CAROL),
                atHome(DAN, DishChoice.SIDES),
                pickup(CAROL)))
                .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ACCEPTED,
                        RegistrationDecision.NOTHING_TO_PICK_UP, RegistrationDecision.ACCEPTED,
                        RegistrationDecision.ACCEPTED);
    }

    @Test
    void r4_a_pickup_never_outnumbers_the_home_preparations() {
        var evening = evening(atHome(BOB, DishChoice.MAIN_DISH), pickup(ALICE));

        assertThat(evening.unmatchedHomePreparations()).isZero();
        assertThat(decide(evening, pickup(CAROL), DURING_SEASON)).isEqualTo(RegistrationDecision.NOTHING_TO_PICK_UP);
    }

    @Test
    void r1_still_bounds_the_dinner_presences_of_pickups() {
        var onePresence = Needs.of(List.of(
                new Need(Post.MAIN_DISH, 1, 1),
                new Need(Post.SIDES, 1, 1),
                new Need(Post.DINNER_PRESENCE, 1, 1),
                new Need(Post.NIGHT, 1, 2),
                new Need(Post.BREAKFAST, 1, 1)));

        assertThat(inSequence(onePresence,
                atHome(BOB, DishChoice.MAIN_DISH),
                atHome(CAROL, DishChoice.SIDES),
                pickup(ALICE),
                pickup(DAN)))
                .containsExactly(RegistrationDecision.ACCEPTED, RegistrationDecision.ACCEPTED,
                        RegistrationDecision.ACCEPTED, RegistrationDecision.FULL);
    }

    @Test
    void r1_counts_a_dinner_presence_for_each_prepare_and_share_of_the_same_volunteer() {
        assertThat(inSequence(
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                Registration.by(BOB, RegistrationType.NIGHT_AND_DINNER, null)))
                .containsOnly(RegistrationDecision.ACCEPTED);
        var evening = evening(
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                Registration.by(ALICE, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES));

        assertThat(evening.remaining(Post.DINNER_PRESENCE)).isZero();
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
    void r7_refuses_an_evening_outside_the_season() {
        var outside = new Evening(LocalDate.of(2027, 3, 21), Needs.defaults(), List.of());

        assertThat(decide(outside, Registration.by(ALICE, RegistrationType.BREAKFAST, null), DURING_SEASON))
                .isEqualTo(RegistrationDecision.OUT_OF_SEASON);
    }

    @Test
    void decision_needs_a_volunteer() {
        var anonymous = Registration.of(RegistrationType.BREAKFAST);

        assertThatIllegalArgumentException().isThrownBy(() -> decide(evening(), anonymous, DURING_SEASON));
    }
}
