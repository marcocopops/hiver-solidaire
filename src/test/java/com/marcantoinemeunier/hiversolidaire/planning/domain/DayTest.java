package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Affichage par journée, données par soirée (ADR-0009) : la carte du jour D montre le petit-déjeuner de la soirée
 * D-1, puis le dîner et la nuit de la soirée D. R5 s'évalue sur les postes de la journée.
 */
class DayTest {

    private static final LocalDate FIRST = LocalDate.of(2027, 1, 4);
    private static final LocalDate SECOND = FIRST.plusDays(1);
    private static final LocalDate LAST = FIRST.plusDays(2);

    private final UUID alice = UUID.randomUUID();

    private static Evening evening(LocalDate date, Registration... registrations) {
        return new Evening(date, Needs.defaults(), List.of(registrations));
    }

    /** Dîner et nuit complets, sans petit-déjeuner. */
    private static Registration[] fullDinnerAndNight() {
        return new Registration[] {
            Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
            Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
            Registration.by(UUID.randomUUID(), RegistrationType.NIGHT_AND_DINNER, null)
        };
    }

    private static Registration breakfast(UUID volunteerId) {
        return Registration.by(volunteerId, RegistrationType.BREAKFAST, null);
    }

    @Test
    void a_season_of_three_evenings_has_four_days() {
        var days = Day.group(List.of(evening(FIRST), evening(SECOND), evening(LAST)));

        assertThat(days).extracting(Day::date).containsExactly(FIRST, SECOND, LAST, LAST.plusDays(1));
    }

    @Test
    void evenings_are_grouped_whatever_their_order() {
        var days = Day.group(List.of(evening(SECOND), evening(FIRST)));

        assertThat(days).extracting(Day::date).containsExactly(FIRST, SECOND, SECOND.plusDays(1));
    }

    @Test
    void the_first_day_has_no_breakfast() {
        var first = Day.group(List.of(evening(FIRST), evening(SECOND))).getFirst();

        assertThat(first.posts())
                .containsExactly(Post.MAIN_DISH, Post.SIDES, Post.DINNER_PRESENCE, Post.NIGHT);
    }

    @Test
    void the_day_after_the_last_evening_only_has_breakfast() {
        var last = Day.group(List.of(evening(FIRST), evening(SECOND))).getLast();

        assertThat(last.date()).isEqualTo(SECOND.plusDays(1));
        assertThat(last.posts()).containsExactly(Post.BREAKFAST);
    }

    @Test
    void a_full_day_lists_breakfast_first_then_dinner_then_night() {
        var day = new Day(SECOND, evening(FIRST), evening(SECOND));

        assertThat(day.posts())
                .containsExactly(Post.BREAKFAST, Post.MAIN_DISH, Post.SIDES, Post.DINNER_PRESENCE, Post.NIGHT);
    }

    @Test
    void breakfast_comes_from_the_previous_evening_and_dinner_from_the_same_evening() {
        var previous = evening(FIRST, breakfast(alice));
        var current = evening(SECOND);
        var day = new Day(SECOND, previous, current);

        assertThat(day.eveningOf(Post.BREAKFAST)).containsSame(previous);
        assertThat(day.eveningOf(Post.NIGHT)).containsSame(current);
        assertThat(day.missing(Post.BREAKFAST)).isZero();
    }

    @Test
    void the_previous_evening_breakfast_does_not_show_on_its_own_day() {
        var day = new Day(FIRST, null, evening(FIRST, breakfast(alice)));

        assertThat(day.posts()).doesNotContain(Post.BREAKFAST);
        assertThat(day.registrationsOf(alice)).isEmpty();
        assertThat(day.involves(alice)).isFalse();
    }

    @Test
    void a_day_is_complete_when_every_post_of_the_day_has_its_minimum() {
        var day = new Day(SECOND, evening(FIRST, breakfast(alice)), evening(SECOND, fullDinnerAndNight()));

        assertThat(day.status()).isEqualTo(DayStatus.COMPLETE);
    }

    @Test
    void the_previous_evening_breakfast_decides_the_status_not_the_current_one() {
        // La soirée D a son petit-déjeuner (il compte pour le jour D+1), mais pas la soirée D-1.
        var day = new Day(SECOND, evening(FIRST), evening(SECOND, fullDinnerAndNight()));
        var withOwnBreakfast = new Day(SECOND, evening(FIRST),
                evening(SECOND, Registration.by(UUID.randomUUID(), RegistrationType.PREPARE_AND_SHARE,
                        DishChoice.MAIN_DISH), breakfast(alice)));

        assertThat(day.status()).isEqualTo(DayStatus.INCOMPLETE);
        assertThat(day.missing(Post.BREAKFAST)).isEqualTo(1);
        assertThat(withOwnBreakfast.missing(Post.BREAKFAST)).isEqualTo(1);
    }

    @Test
    void the_first_day_is_complete_without_breakfast() {
        var day = new Day(FIRST, null, evening(FIRST, fullDinnerAndNight()));

        assertThat(day.status()).isEqualTo(DayStatus.COMPLETE);
    }

    @Test
    void the_last_day_is_complete_with_breakfast_alone() {
        var day = new Day(SECOND, evening(FIRST, breakfast(alice)), null);

        assertThat(day.status()).isEqualTo(DayStatus.COMPLETE);
    }

    @Test
    void a_day_without_any_registration_is_incomplete_not_empty() {
        var day = new Day(SECOND, evening(FIRST), evening(SECOND));

        assertThat(day.status()).isEqualTo(DayStatus.INCOMPLETE);
        assertThat(DayStatus.values()).containsExactly(DayStatus.COMPLETE, DayStatus.INCOMPLETE);
        assertThat(day.missing(Post.DINNER_PRESENCE)).isEqualTo(2);
    }

    @Test
    void a_volunteer_can_have_breakfast_and_dinner_on_the_same_day() {
        var day = new Day(SECOND, evening(FIRST, breakfast(alice)),
                evening(SECOND, Registration.by(alice, RegistrationType.NIGHT_AND_DINNER, null)));

        assertThat(day.registrationsOf(alice)).containsExactly(
                new DayRegistration(FIRST, breakfast(alice)),
                new DayRegistration(SECOND, Registration.by(alice, RegistrationType.NIGHT_AND_DINNER, null)));
        assertThat(day.involves(alice)).isTrue();
        assertThat(day.involves(UUID.randomUUID())).isFalse();
    }

    @Test
    void a_day_is_past_from_midnight_the_next_day() {
        var day = new Day(SECOND, evening(FIRST), evening(SECOND));

        assertThat(day.isPast(SECOND)).isFalse();
        assertThat(day.isPast(SECOND.plusDays(1))).isTrue();
    }

    @Test
    void evenings_must_match_the_day() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Day(SECOND, evening(SECOND), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Day(SECOND, null, evening(FIRST)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Day(SECOND, null, null));
    }
}
