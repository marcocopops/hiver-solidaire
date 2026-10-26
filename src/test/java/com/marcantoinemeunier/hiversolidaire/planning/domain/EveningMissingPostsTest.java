package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static com.marcantoinemeunier.hiversolidaire.planning.domain.Registration.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R5 : postes dont le minimum n'est pas couvert, sur une soirée. Le statut se calcule par journée ({@link Day}). */
class EveningMissingPostsTest {

    private static Evening evening(Registration... registrations) {
        return new Evening(LocalDate.of(2027, 1, 4), Needs.defaults(), List.of(registrations));
    }

    private static Registration[] fullEvening() {
        return new Registration[] {
            of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
            of(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
            of(RegistrationType.NIGHT_AND_DINNER),
            of(RegistrationType.BREAKFAST)
        };
    }

    @Test
    void evening_without_registration_misses_every_post() {
        var evening = evening();

        assertThat(evening.missingPosts()).containsExactly(Post.values());
    }

    @Test
    void evening_with_every_minimum_covered_misses_nothing() {
        var evening = evening(fullEvening());

        assertThat(evening.missingPosts()).isEmpty();
    }

    @Test
    void one_night_volunteer_covers_the_night_minimum() {
        // Nuit : 1 minimum, 2 maximum. La seconde place reste libre.
        var evening = evening(fullEvening());

        assertThat(evening.remaining(Post.NIGHT)).isEqualTo(1);
        assertThat(evening.missing(Post.NIGHT)).isZero();
    }

    @Test
    void evening_with_some_registrations_lists_the_missing_posts() {
        var evening = evening(of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH));

        assertThat(evening.missingPosts())
                .containsExactly(Post.SIDES, Post.DINNER_PRESENCE, Post.NIGHT, Post.BREAKFAST);
    }

    @Test
    void night_volunteer_alone_leaves_dinner_presences_missing() {
        var evening = evening(
                of(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
                of(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES),
                of(RegistrationType.NIGHT_AND_DINNER),
                of(RegistrationType.BREAKFAST));

        assertThat(evening.missingPosts()).containsExactly(Post.DINNER_PRESENCE);
    }
}
