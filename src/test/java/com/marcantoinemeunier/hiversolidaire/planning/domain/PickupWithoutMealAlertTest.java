package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Alerte « récupération sans repas » (R4, lot 3) : la désinscription d'une préparation chez soi déjà récupérée est
 * acceptée, la récupération reste, et les coordinateurs sont alertés pour prévenir la personne qui devait récupérer.
 */
class PickupWithoutMealAlertTest {

    private static final LocalDate DATE = LocalDate.of(2027, 1, 12);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    private static Evening evening(Registration... registrations) {
        return new Evening(DATE, Needs.defaults(), List.of(registrations));
    }

    private Registration aliceAtHome(DishChoice dish) {
        return Registration.by(alice, RegistrationType.PREPARE_AT_HOME, dish);
    }

    private Registration pickup(UUID volunteer) {
        return Registration.by(volunteer, RegistrationType.PICK_UP_AND_SHARE, null);
    }

    @Test
    void cancelling_a_home_preparation_already_picked_up_triggers_the_alert() {
        var before = evening(aliceAtHome(DishChoice.MAIN_DISH), pickup(bob));

        var after = before.without(aliceAtHome(DishChoice.MAIN_DISH));

        assertThat(after.pickupsWithoutMeal()).isEqualTo(1);
        assertThat(PickupWithoutMealAlert.triggeredBy(before, after)).isTrue();
    }

    @Test
    void cancelling_a_home_preparation_nobody_picks_up_does_not_trigger_it() {
        var before = evening(aliceAtHome(DishChoice.MAIN_DISH));

        assertThat(PickupWithoutMealAlert.triggeredBy(before, before.without(aliceAtHome(DishChoice.MAIN_DISH))))
                .isFalse();
    }

    @Test
    void another_home_preparation_still_waiting_for_its_pickup_keeps_the_pickup_busy() {
        var before = evening(aliceAtHome(DishChoice.MAIN_DISH), aliceAtHome(DishChoice.SIDES), pickup(bob));

        assertThat(PickupWithoutMealAlert.triggeredBy(before, before.without(aliceAtHome(DishChoice.SIDES))))
                .isFalse();
    }

    @Test
    void cancelling_the_pickup_itself_does_not_trigger_it() {
        var before = evening(aliceAtHome(DishChoice.MAIN_DISH), pickup(bob));

        assertThat(PickupWithoutMealAlert.triggeredBy(before, before.without(pickup(bob)))).isFalse();
    }

    @Test
    void a_pickup_already_without_meal_does_not_trigger_it_again() {
        var before = evening(pickup(bob), Registration.by(carol, RegistrationType.BREAKFAST, null));

        assertThat(PickupWithoutMealAlert.triggeredBy(before,
                before.without(Registration.by(carol, RegistrationType.BREAKFAST, null)))).isFalse();
    }

    @Test
    void deleting_an_account_with_a_picked_up_home_preparation_triggers_it() {
        var before = evening(aliceAtHome(DishChoice.MAIN_DISH), Registration.by(alice, RegistrationType.BREAKFAST,
                null), pickup(bob));

        assertThat(PickupWithoutMealAlert.triggeredBy(before, before.without(alice))).isTrue();
    }
}
