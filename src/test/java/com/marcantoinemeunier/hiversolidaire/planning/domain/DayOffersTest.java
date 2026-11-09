package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * « Je participe » sur une journée : le petit-déjeuner s'inscrit sur la soirée D-1, le reste sur la soirée D.
 * R1 à R4 et R7 s'appliquent soirée par soirée ; R2 porte sur le poste (ADR-0011) et « Récupérer » n'est proposé
 * que pour un repas préparé chez soi qui attend sa récupération (R4).
 */
class DayOffersTest {

    private static final LocalDate FIRST = LocalDate.of(2027, 1, 4);
    private static final LocalDate DAY = LocalDate.of(2027, 1, 12);
    private static final Season SEASON = new Season(FIRST, LocalDate.of(2027, 3, 20),
            paris(LocalDateTime.of(2026, 12, 14, 0, 0)));
    private static final Instant BEFORE = paris(LocalDateTime.of(2027, 1, 5, 12, 0));

    private final UUID alice = UUID.randomUUID();

    private static Instant paris(LocalDateTime dateTime) {
        return dateTime.atZone(ParisTime.ZONE).toInstant();
    }

    private static Evening evening(LocalDate date, Registration... registrations) {
        return new Evening(date, Needs.defaults(), List.of(registrations));
    }

    private static Offer offer(LocalDate evening, RegistrationType type, DishChoice dish) {
        return new Offer(evening, type, dish);
    }

    /** Tout ce qu'une journée vide propose : pas de récupération, faute de repas préparé chez soi (R4). */
    private static List<Offer> allOffers(LocalDate day) {
        var previous = day.minusDays(1);
        return List.of(
                offer(previous, RegistrationType.BREAKFAST, null),
                offer(day, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                offer(day, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                offer(day, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                offer(day, RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES),
                offer(day, RegistrationType.NIGHT_AND_DINNER, null));
    }

    private static Offer pickupOn(LocalDate day) {
        return offer(day, RegistrationType.PICK_UP_AND_SHARE, null);
    }

    @Test
    void breakfast_is_offered_on_the_previous_evening_and_the_rest_on_the_same_evening() {
        var day = new Day(DAY, evening(DAY.minusDays(1)), evening(DAY));

        var offers = day.offers(SEASON, alice, BEFORE);

        assertThat(offers.blocking()).isNull();
        assertThat(offers.available()).containsExactlyElementsOf(allOffers(DAY));
    }

    @Test
    void r2_applies_per_post_the_night_leaves_the_dishes_and_the_breakfast() {
        var day = new Day(DAY, evening(DAY.minusDays(1)),
                evening(DAY, Registration.by(alice, RegistrationType.NIGHT_AND_DINNER, null)));

        var offers = day.offers(SEASON, alice, BEFORE);

        assertThat(offers.available()).containsExactly(
                offer(DAY.minusDays(1), RegistrationType.BREAKFAST, null),
                offer(DAY, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                offer(DAY, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                offer(DAY, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                offer(DAY, RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES));
        assertThat(offers.blocking()).isNull();
    }

    @Test
    void r2_a_breakfast_does_not_block_the_same_day_dinner() {
        var day = new Day(DAY, evening(DAY.minusDays(1), Registration.by(alice, RegistrationType.BREAKFAST, null)),
                evening(DAY));

        assertThat(day.offers(SEASON, alice, BEFORE).available()).hasSize(5)
                .allSatisfy(offer -> assertThat(offer.eveningDate()).isEqualTo(DAY));
    }

    @Test
    void a_volunteer_who_holds_everything_left_gets_nothing_and_the_day_is_full_for_them() {
        var day = new Day(DAY, evening(DAY.minusDays(1), Registration.by(alice, RegistrationType.BREAKFAST, null)),
                evening(DAY,
                        Registration.by(alice, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                        Registration.by(alice, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                        Registration.by(alice, RegistrationType.NIGHT_AND_DINNER, null)));

        var offers = day.offers(SEASON, alice, BEFORE);

        assertThat(offers.available()).isEmpty();
        assertThat(offers.blocking()).isEqualTo(RegistrationDecision.FULL);
    }

    @Test
    void the_pickup_is_offered_only_while_a_home_preparation_waits_for_it() {
        var bob = UUID.randomUUID();
        var atHome = Registration.by(bob, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH);
        var pickedUp = Registration.by(UUID.randomUUID(), RegistrationType.PICK_UP_AND_SHARE, null);

        assertThat(new Day(DAY, evening(DAY.minusDays(1)), evening(DAY)).offers(SEASON, alice, BEFORE).available())
                .doesNotContain(pickupOn(DAY));
        assertThat(new Day(DAY, evening(DAY.minusDays(1)), evening(DAY, atHome)).offers(SEASON, alice, BEFORE)
                .available()).contains(pickupOn(DAY));
        assertThat(new Day(DAY, evening(DAY.minusDays(1)), evening(DAY, atHome, pickedUp))
                .offers(SEASON, alice, BEFORE).available()).doesNotContain(pickupOn(DAY));
    }

    @Test
    void the_pickup_is_not_offered_to_a_volunteer_with_another_post_of_the_same_dinner() {
        var atHome = Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH);
        var day = new Day(DAY, evening(DAY.minusDays(1)),
                evening(DAY, atHome, Registration.by(alice, RegistrationType.NIGHT_AND_DINNER, null)));

        assertThat(day.offers(SEASON, alice, BEFORE).available()).containsExactly(
                offer(DAY.minusDays(1), RegistrationType.BREAKFAST, null),
                offer(DAY, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                offer(DAY, RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES));
    }

    @Test
    void a_volunteer_with_a_pickup_is_offered_only_the_breakfast() {
        var day = new Day(DAY, evening(DAY.minusDays(1)), evening(DAY,
                Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                Registration.by(alice, RegistrationType.PICK_UP_AND_SHARE, null)));

        assertThat(day.offers(SEASON, alice, BEFORE).available())
                .containsExactly(offer(DAY.minusDays(1), RegistrationType.BREAKFAST, null));
    }

    @Test
    void the_same_day_breakfast_is_not_offered_since_its_evening_is_past() {
        var day = new Day(DAY, evening(DAY.minusDays(1)), evening(DAY));
        var thatMorning = paris(LocalDateTime.of(2027, 1, 12, 6, 0));

        var offers = day.offers(SEASON, alice, thatMorning);

        assertThat(offers.available()).hasSize(5).noneMatch(offer -> offer.type() == RegistrationType.BREAKFAST);
    }

    @Test
    void the_next_day_breakfast_is_still_offered_on_the_evening_itself() {
        var tomorrow = new Day(DAY.plusDays(1), evening(DAY), evening(DAY.plusDays(1)));
        var thatEvening = paris(LocalDateTime.of(2027, 1, 12, 22, 0));

        assertThat(tomorrow.offers(SEASON, alice, thatEvening).available())
                .contains(offer(DAY, RegistrationType.BREAKFAST, null));
    }

    @Test
    void the_first_day_offers_no_breakfast() {
        var first = new Day(FIRST, null, evening(FIRST));
        var afterOpening = paris(LocalDateTime.of(2026, 12, 20, 12, 0));

        assertThat(first.offers(SEASON, alice, afterOpening).available())
                .noneMatch(offer -> offer.type() == RegistrationType.BREAKFAST).hasSize(5);
    }

    @Test
    void the_last_day_only_offers_breakfast() {
        var last = LocalDate.of(2027, 3, 20);
        var day = new Day(last.plusDays(1), evening(last), null);

        assertThat(day.offers(SEASON, alice, BEFORE).available())
                .containsExactly(offer(last, RegistrationType.BREAKFAST, null));
    }

    @Test
    void r1_and_r3_remove_the_taken_places() {
        var day = new Day(DAY, evening(DAY.minusDays(1), Registration.by(UUID.randomUUID(), RegistrationType.BREAKFAST,
                null)), evening(DAY, Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AT_HOME,
                DishChoice.MAIN_DISH)));

        assertThat(day.offers(SEASON, alice, BEFORE).available()).containsExactly(
                offer(DAY, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                offer(DAY, RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES),
                offer(DAY, RegistrationType.PICK_UP_AND_SHARE, null),
                offer(DAY, RegistrationType.NIGHT_AND_DINNER, null));
    }

    @Test
    void a_full_day_is_blocked_as_full() {
        var others = new Registration[] {
            Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
            Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
            Registration.by(UUID.randomUUID(), RegistrationType.NIGHT_AND_DINNER, null),
            Registration.by(UUID.randomUUID(), RegistrationType.NIGHT_AND_DINNER, null)
        };
        var day = new Day(DAY, evening(DAY.minusDays(1), Registration.by(UUID.randomUUID(),
                RegistrationType.BREAKFAST, null)), evening(DAY, others));

        var offers = day.offers(SEASON, alice, BEFORE);

        assertThat(offers.available()).isEmpty();
        assertThat(offers.blocking()).isEqualTo(RegistrationDecision.FULL);
    }

    @Test
    void nothing_is_offered_before_opening() {
        var day = new Day(DAY, evening(DAY.minusDays(1)), evening(DAY));

        var offers = day.offers(SEASON, alice, paris(LocalDateTime.of(2026, 12, 1, 12, 0)));

        assertThat(offers.available()).isEmpty();
        assertThat(offers.blocking()).isEqualTo(RegistrationDecision.NOT_OPEN_YET);
    }
}
