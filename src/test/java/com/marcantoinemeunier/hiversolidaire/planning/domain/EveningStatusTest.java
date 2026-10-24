package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static com.marcantoinemeunier.hiversolidaire.planning.domain.Registration.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R5 Statut : complète (tous les minimums couverts), partielle ou vide. */
class EveningStatusTest {

    private static Evening evening(Registration... registrations) {
        return new Evening(LocalDate.of(2027, 1, 4), false, Needs.defaults(), List.of(registrations));
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
    void evening_without_registration_is_empty() {
        var evening = evening();

        assertThat(evening.status()).isEqualTo(EveningStatus.EMPTY);
        assertThat(evening.missingPosts()).containsExactly(Post.values());
    }

    @Test
    void evening_with_every_minimum_covered_is_complete() {
        var evening = evening(fullEvening());

        assertThat(evening.status()).isEqualTo(EveningStatus.COMPLETE);
        assertThat(evening.missingPosts()).isEmpty();
    }

    @Test
    void one_night_volunteer_is_enough_for_a_complete_evening() {
        // Nuit : 1 minimum, 2 maximum. La seconde place reste libre sans rendre la soirée partielle.
        var evening = evening(fullEvening());

        assertThat(evening.remaining(Post.NIGHT)).isEqualTo(1);
        assertThat(evening.status()).isEqualTo(EveningStatus.COMPLETE);
    }

    @Test
    void evening_with_some_registrations_but_missing_posts_is_partial() {
        var evening = evening(of(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH));

        assertThat(evening.status()).isEqualTo(EveningStatus.PARTIAL);
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

        assertThat(evening.status()).isEqualTo(EveningStatus.PARTIAL);
        assertThat(evening.missingPosts()).containsExactly(Post.DINNER_PRESENCE);
    }
}
