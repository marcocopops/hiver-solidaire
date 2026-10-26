package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Format d'affichage d'une journée (cahier des charges, §5, ADR-0009) : « Lun. 4 janv. ». */
class DayLabelFormatterTest {

    @Test
    void formats_a_day_with_its_abbreviated_month() {
        assertThat(DayLabelFormatter.format(LocalDate.of(2027, 1, 4))).isEqualTo("Lun. 4 janv.");
        assertThat(DayLabelFormatter.format(LocalDate.of(2027, 3, 21))).isEqualTo("Dim. 21 mars");
    }

    @Test
    void the_first_of_the_month_is_written_1er() {
        assertThat(DayLabelFormatter.format(LocalDate.of(2027, 2, 1))).isEqualTo("Lun. 1er févr.");
    }

    @Test
    void inline_form_is_lowercase_for_use_inside_a_sentence() {
        assertThat(DayLabelFormatter.inline(LocalDate.of(2027, 1, 5))).isEqualTo("mar. 5 janv.");
    }

    @Test
    void a_deadline_gives_the_day_and_the_time() {
        assertThat(DayLabelFormatter.deadline(LocalDate.of(2027, 1, 7).atTime(23, 59)))
                .isEqualTo("jeu. 7 janv. à 23 h 59");
    }

    @Test
    void an_activity_is_labelled_by_the_day_it_takes_place() {
        var evening = LocalDate.of(2027, 1, 4);

        assertThat(ActivityLabel.of(RegistrationType.BREAKFAST, evening))
                .isEqualTo(new ActivityLabel("activity.BREAKFAST", "mar. 5 janv."));
        assertThat(ActivityLabel.of(RegistrationType.NIGHT_AND_DINNER, evening))
                .isEqualTo(new ActivityLabel("activity.NIGHT_AND_DINNER", "lun. 4 janv."));
        assertThat(ActivityLabel.of(RegistrationType.PREPARE_AT_HOME, evening))
                .isEqualTo(new ActivityLabel("activity.DINNER", "lun. 4 janv."));
    }
}
