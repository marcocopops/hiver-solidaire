package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static com.marcantoinemeunier.hiversolidaire.planning.domain.Registration.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R4 Récupération : chaque « préparer chez soi » est couvert par un « récupérer et partager » distinct. */
class PickupMatchingTest {

    private static Evening evening(Registration... registrations) {
        return new Evening(LocalDate.of(2027, 1, 4), false, Needs.defaults(), List.of(registrations));
    }

    @Test
    void no_home_preparation_needs_no_pickup() {
        var evening = evening(of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH));

        assertThat(evening.unmatchedHomePreparations()).isZero();
        assertThat(evening.needsPickupAlert()).isFalse();
    }

    @Test
    void home_preparation_without_pickup_raises_an_alert() {
        var evening = evening(of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH));

        assertThat(evening.unmatchedHomePreparations()).isEqualTo(1);
        assertThat(evening.needsPickupAlert()).isTrue();
    }

    @Test
    void one_pickup_covers_one_home_preparation() {
        var evening = evening(
                of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                of(RegistrationType.PICK_UP_AND_SHARE));

        assertThat(evening.needsPickupAlert()).isFalse();
    }

    @Test
    void two_home_preparations_need_two_pickups() {
        var evening = evening(
                of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                of(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES),
                of(RegistrationType.PICK_UP_AND_SHARE));

        assertThat(evening.unmatchedHomePreparations()).isEqualTo(1);
    }

    @Test
    void extra_pickups_do_not_produce_a_negative_count() {
        var evening = evening(of(RegistrationType.PICK_UP_AND_SHARE), of(RegistrationType.PICK_UP_AND_SHARE));

        assertThat(evening.unmatchedHomePreparations()).isZero();
    }
}
