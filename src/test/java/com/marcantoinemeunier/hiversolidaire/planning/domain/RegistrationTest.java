package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** R3 Préparation et postes couverts par type d'inscription (cahier des charges, §5). */
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
}
