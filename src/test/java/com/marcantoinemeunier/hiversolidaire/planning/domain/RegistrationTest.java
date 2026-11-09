package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * R3 Préparation, postes couverts par type d'inscription (cahier des charges, §5) et poste propre de chaque
 * inscription au sens de R2 (un poste par bénévole et par soirée, ADR-0011).
 */
class RegistrationTest {

    @Test
    void prepare_and_share_covers_the_chosen_dish_and_one_dinner_presence() {
        var registration = Registration.of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH);

        assertThat(registration.coveredPosts()).containsExactlyInAnyOrder(Post.MAIN_DISH, Post.DINNER_PRESENCE);
    }

    @Test
    void prepare_at_home_covers_the_chosen_dish_only() {
        var registration = Registration.of(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES);

        assertThat(registration.coveredPosts()).containsExactly(Post.SIDES);
    }

    @Test
    void pick_up_and_share_covers_one_dinner_presence() {
        assertThat(Registration.of(RegistrationType.PICK_UP_AND_SHARE).coveredPosts())
                .containsExactly(Post.DINNER_PRESENCE);
    }

    @Test
    void night_does_not_count_as_a_dinner_presence() {
        assertThat(Registration.of(RegistrationType.NIGHT_AND_DINNER).coveredPosts()).containsExactly(Post.NIGHT);
    }

    @Test
    void breakfast_covers_breakfast() {
        assertThat(Registration.of(RegistrationType.BREAKFAST).coveredPosts()).containsExactly(Post.BREAKFAST);
    }

    @Test
    void preparing_requires_a_dish_choice() {
        assertThatIllegalArgumentException().isThrownBy(() -> Registration.of(RegistrationType.PREPARE_AND_SHARE));
        assertThatIllegalArgumentException().isThrownBy(() -> Registration.of(RegistrationType.PREPARE_AT_HOME));
    }

    @Test
    void other_types_reject_a_dish_choice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Registration.of(RegistrationType.BREAKFAST, DishChoice.MAIN_DISH));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Registration.of(RegistrationType.PICK_UP_AND_SHARE, DishChoice.SIDES));
    }

    @Test
    void the_own_post_is_the_dish_for_those_who_prepare() {
        assertThat(Registration.of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH).ownPost())
                .isEqualTo(Post.MAIN_DISH);
        assertThat(Registration.of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH).ownPost())
                .isEqualTo(Post.MAIN_DISH);
        assertThat(Registration.of(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES).ownPost())
                .isEqualTo(Post.SIDES);
    }

    @Test
    void the_own_post_of_the_other_types_is_their_only_post() {
        assertThat(Registration.of(RegistrationType.PICK_UP_AND_SHARE).ownPost()).isEqualTo(Post.DINNER_PRESENCE);
        assertThat(Registration.of(RegistrationType.NIGHT_AND_DINNER).ownPost()).isEqualTo(Post.NIGHT);
        assertThat(Registration.of(RegistrationType.BREAKFAST).ownPost()).isEqualTo(Post.BREAKFAST);
    }

    @Test
    void two_registrations_on_the_same_post_conflict() {
        var alice = UUID.randomUUID();

        assertThat(Registration.by(alice, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH)
                .conflictsWith(Registration.by(alice, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH)))
                .isTrue();
        assertThat(Registration.of(RegistrationType.NIGHT_AND_DINNER)
                .conflictsWith(Registration.of(RegistrationType.NIGHT_AND_DINNER))).isTrue();
    }

    @Test
    void distinct_posts_do_not_conflict() {
        var main = Registration.of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH);

        assertThat(main.conflictsWith(Registration.of(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES))).isFalse();
        assertThat(main.conflictsWith(Registration.of(RegistrationType.NIGHT_AND_DINNER))).isFalse();
        assertThat(main.conflictsWith(Registration.of(RegistrationType.BREAKFAST))).isFalse();
        assertThat(Registration.of(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES)
                .conflictsWith(Registration.of(RegistrationType.NIGHT_AND_DINNER))).isFalse();
    }

    @Test
    void a_pickup_conflicts_with_every_other_dinner_post_but_not_with_breakfast() {
        var pickup = Registration.of(RegistrationType.PICK_UP_AND_SHARE);

        for (var other : new Registration[] {
            Registration.of(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
            Registration.of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
            Registration.of(RegistrationType.NIGHT_AND_DINNER)}) {
            assertThat(pickup.conflictsWith(other)).as(other.toString()).isTrue();
            assertThat(other.conflictsWith(pickup)).as(other.toString()).isTrue();
        }
        assertThat(pickup.conflictsWith(Registration.of(RegistrationType.BREAKFAST))).isFalse();
        assertThat(Registration.of(RegistrationType.BREAKFAST).conflictsWith(pickup)).isFalse();
    }
}
