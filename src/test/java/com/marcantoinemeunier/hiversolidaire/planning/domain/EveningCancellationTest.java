package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Une inscription annulée ne compte plus : R1, R2, R3 et R5 sont recalculés sans elle. */
class EveningCancellationTest {

    private static final LocalDate DATE = LocalDate.of(2027, 1, 12);
    private static final Season SEASON = new Season(
            LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 20), paris(LocalDateTime.of(2026, 12, 14, 0, 0)));
    private static final Instant BEFORE_DEADLINE = paris(LocalDateTime.of(2027, 1, 10, 12, 0));
    private static final Instant AFTER_DEADLINE = paris(LocalDateTime.of(2027, 1, 12, 9, 0));

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private static Instant paris(LocalDateTime dateTime) {
        return dateTime.atZone(ParisTime.ZONE).toInstant();
    }

    private static Evening evening(Registration... registrations) {
        return new Evening(DATE, false, Needs.defaults(), List.of(registrations));
    }

    @Test
    void cancelling_frees_the_place_for_another_volunteer() {
        var evening = evening(Registration.by(alice, RegistrationType.BREAKFAST, null));
        assertThat(evening.decide(SEASON, Registration.by(bob, RegistrationType.BREAKFAST, null), BEFORE_DEADLINE))
                .isEqualTo(RegistrationDecision.FULL);

        var after = evening.without(alice);

        assertThat(after.remaining(Post.BREAKFAST)).isEqualTo(1);
        assertThat(after.decide(SEASON, Registration.by(bob, RegistrationType.BREAKFAST, null), BEFORE_DEADLINE))
                .isEqualTo(RegistrationDecision.ACCEPTED);
    }

    @Test
    void cancelling_lets_the_same_volunteer_register_again() {
        var after = evening(Registration.by(alice, RegistrationType.BREAKFAST, null)).without(alice);

        assertThat(after.decide(SEASON, Registration.by(alice, RegistrationType.NIGHT_AND_DINNER, null),
                BEFORE_DEADLINE)).isEqualTo(RegistrationDecision.ACCEPTED);
    }

    @Test
    void cancelling_frees_the_dish() {
        var evening = evening(Registration.by(alice, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH));

        var after = evening.without(alice);

        assertThat(after.decide(SEASON, Registration.by(bob, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                BEFORE_DEADLINE)).isEqualTo(RegistrationDecision.ACCEPTED);
    }

    @Test
    void status_is_recomputed_without_the_cancelled_registration() {
        var full = evening(
                Registration.by(alice, RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
                Registration.by(bob, RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
                Registration.by(UUID.randomUUID(), RegistrationType.NIGHT_AND_DINNER, null),
                Registration.by(UUID.randomUUID(), RegistrationType.BREAKFAST, null));
        assertThat(full.status()).isEqualTo(EveningStatus.COMPLETE);

        var partial = full.without(alice);
        assertThat(partial.status()).isEqualTo(EveningStatus.PARTIAL);
        assertThat(partial.missingPosts()).containsExactly(Post.MAIN_DISH, Post.DINNER_PRESENCE);

        assertThat(evening(Registration.by(alice, RegistrationType.BREAKFAST, null)).without(alice).status())
                .isEqualTo(EveningStatus.EMPTY);
    }

    @Test
    void without_keeps_the_other_registrations() {
        var evening = evening(Registration.by(alice, RegistrationType.BREAKFAST, null),
                Registration.by(bob, RegistrationType.NIGHT_AND_DINNER, null));

        assertThat(evening.without(alice).registrations())
                .containsExactly(Registration.by(bob, RegistrationType.NIGHT_AND_DINNER, null));
        assertThat(evening.without(UUID.randomUUID()).registrations()).hasSize(2);
    }

    @Test
    void cancellation_is_accepted_before_the_deadline() {
        var evening = evening(Registration.by(alice, RegistrationType.BREAKFAST, null));

        assertThat(evening.decideCancellation(alice, BEFORE_DEADLINE)).isEqualTo(CancellationDecision.ACCEPTED);
    }

    @Test
    void cancellation_is_refused_after_the_deadline() {
        var evening = evening(Registration.by(alice, RegistrationType.BREAKFAST, null));

        assertThat(evening.decideCancellation(alice, AFTER_DEADLINE)).isEqualTo(CancellationDecision.TOO_LATE);
    }

    @Test
    void cancellation_of_another_volunteer_registration_is_refused() {
        var evening = evening(Registration.by(alice, RegistrationType.BREAKFAST, null));

        assertThat(evening.decideCancellation(bob, BEFORE_DEADLINE)).isEqualTo(CancellationDecision.NOT_REGISTERED);
    }

    @Test
    void cancellation_is_allowed_on_a_closed_evening_before_the_deadline() {
        var closed = new Evening(DATE, true, Needs.defaults(),
                List.of(Registration.by(alice, RegistrationType.BREAKFAST, null)));

        assertThat(closed.decideCancellation(alice, BEFORE_DEADLINE)).isEqualTo(CancellationDecision.ACCEPTED);
    }
}
