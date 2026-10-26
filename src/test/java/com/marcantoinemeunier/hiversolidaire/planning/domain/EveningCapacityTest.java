package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static com.marcantoinemeunier.hiversolidaire.planning.domain.Registration.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R1 Capacité (calcul en lecture) et R3 « un plat de chaque par soirée ». */
class EveningCapacityTest {

    private static final LocalDate DATE = LocalDate.of(2027, 1, 4);

    private static Evening evening(Registration... registrations) {
        return new Evening(DATE, Needs.defaults(), List.of(registrations));
    }

    @Test
    void empty_evening_has_every_place_left() {
        var evening = evening();

        assertThat(evening.remaining(Post.MAIN_DISH)).isEqualTo(1);
        assertThat(evening.remaining(Post.SIDES)).isEqualTo(1);
        assertThat(evening.remaining(Post.DINNER_PRESENCE)).isEqualTo(2);
        assertThat(evening.remaining(Post.NIGHT)).isEqualTo(2);
        assertThat(evening.remaining(Post.BREAKFAST)).isEqualTo(1);
    }

    @Test
    void registrations_consume_the_places_of_every_post_they_cover() {
        var evening = evening(
                of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                of(RegistrationType.PICK_UP_AND_SHARE));

        assertThat(evening.occupied(Post.MAIN_DISH)).isEqualTo(1);
        assertThat(evening.occupied(Post.DINNER_PRESENCE)).isEqualTo(2);
        assertThat(evening.remaining(Post.MAIN_DISH)).isZero();
        assertThat(evening.remaining(Post.DINNER_PRESENCE)).isZero();
        assertThat(evening.remaining(Post.SIDES)).isEqualTo(1);
    }

    @Test
    void night_volunteer_does_not_take_a_dinner_presence() {
        var evening = evening(of(RegistrationType.NIGHT_AND_DINNER), of(RegistrationType.NIGHT_AND_DINNER));

        assertThat(evening.remaining(Post.NIGHT)).isZero();
        assertThat(evening.remaining(Post.DINNER_PRESENCE)).isEqualTo(2);
    }

    @Test
    void a_type_is_available_only_if_every_covered_post_has_a_place_left() {
        var evening = evening(
                of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                of(RegistrationType.PICK_UP_AND_SHARE),
                of(RegistrationType.PICK_UP_AND_SHARE));

        // Il reste les accompagnements, mais plus aucune présence au dîner.
        assertThat(evening.isAvailable(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES)).isTrue();
        assertThat(evening.isAvailable(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES)).isFalse();
        assertThat(evening.isAvailable(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH)).isFalse();
        assertThat(evening.isAvailable(RegistrationType.NIGHT_AND_DINNER, null)).isTrue();
    }

    @Test
    void only_one_main_dish_and_one_side_dish_per_evening() {
        var evening = evening(
                of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                of(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES));

        assertThat(evening.isAvailable(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH)).isFalse();
        assertThat(evening.isAvailable(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES)).isFalse();
    }

    @Test
    void remaining_never_goes_below_zero() {
        // Peut arriver si un coordinateur abaisse un besoin après des inscriptions.
        var needs = Needs.of(List.of(
                new Need(Post.MAIN_DISH, 1, 1),
                new Need(Post.SIDES, 1, 1),
                new Need(Post.DINNER_PRESENCE, 2, 2),
                new Need(Post.NIGHT, 1, 1),
                new Need(Post.BREAKFAST, 1, 1)));
        var evening = new Evening(DATE, needs,
                List.of(of(RegistrationType.NIGHT_AND_DINNER), of(RegistrationType.NIGHT_AND_DINNER)));

        assertThat(evening.remaining(Post.NIGHT)).isZero();
    }
}
