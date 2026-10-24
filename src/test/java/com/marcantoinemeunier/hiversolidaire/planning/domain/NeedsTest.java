package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class NeedsTest {

    @Test
    void defaults_match_the_needs_table_of_the_specification() {
        var needs = Needs.defaults();

        assertThat(needs.of(Post.MAIN_DISH)).isEqualTo(new Need(Post.MAIN_DISH, 1, 1));
        assertThat(needs.of(Post.SIDES)).isEqualTo(new Need(Post.SIDES, 1, 1));
        assertThat(needs.of(Post.DINNER_PRESENCE)).isEqualTo(new Need(Post.DINNER_PRESENCE, 2, 2));
        assertThat(needs.of(Post.NIGHT)).isEqualTo(new Need(Post.NIGHT, 1, 2));
        assertThat(needs.of(Post.BREAKFAST)).isEqualTo(new Need(Post.BREAKFAST, 1, 1));
    }

    @Test
    void needs_are_configurable() {
        var needs = Needs.of(List.of(
                new Need(Post.MAIN_DISH, 1, 1),
                new Need(Post.SIDES, 1, 1),
                new Need(Post.DINNER_PRESENCE, 3, 3),
                new Need(Post.NIGHT, 2, 2),
                new Need(Post.BREAKFAST, 1, 2)));

        assertThat(needs.of(Post.DINNER_PRESENCE).minimum()).isEqualTo(3);
        assertThat(needs.of(Post.NIGHT).maximum()).isEqualTo(2);
    }

    @Test
    void every_post_must_have_a_need() {
        assertThatIllegalArgumentException().isThrownBy(() -> Needs.of(List.of(new Need(Post.MAIN_DISH, 1, 1))));
    }

    @Test
    void a_post_cannot_have_two_needs() {
        assertThatIllegalArgumentException().isThrownBy(() -> Needs.of(List.of(
                new Need(Post.MAIN_DISH, 1, 1),
                new Need(Post.MAIN_DISH, 1, 1),
                new Need(Post.SIDES, 1, 1),
                new Need(Post.DINNER_PRESENCE, 2, 2),
                new Need(Post.NIGHT, 1, 2),
                new Need(Post.BREAKFAST, 1, 1))));
    }

    @Test
    void minimum_cannot_exceed_maximum_nor_be_negative() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Need(Post.NIGHT, 3, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> new Need(Post.NIGHT, -1, 2));
    }
}
