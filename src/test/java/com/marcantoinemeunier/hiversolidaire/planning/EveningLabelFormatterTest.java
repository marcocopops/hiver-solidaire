package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Format d'affichage d'une soirée (cahier des charges, §5) : « Lun. 5 → mar. 6 janv. ». */
class EveningLabelFormatterTest {

    @Test
    void same_month_shows_the_month_once() {
        assertThat(EveningLabelFormatter.format(LocalDate.of(2026, 1, 5))).isEqualTo("Lun. 5 → mar. 6 janv.");
    }

    @Test
    void month_change_shows_both_months_and_first_of_month() {
        assertThat(EveningLabelFormatter.format(LocalDate.of(2027, 1, 31))).isEqualTo("Dim. 31 janv. → lun. 1er févr.");
    }

    @Test
    void year_change_is_handled() {
        assertThat(EveningLabelFormatter.format(LocalDate.of(2026, 12, 31))).isEqualTo("Jeu. 31 déc. → ven. 1er janv.");
    }

    @Test
    void first_of_month_as_evening_uses_ordinal() {
        assertThat(EveningLabelFormatter.format(LocalDate.of(2027, 3, 1))).isEqualTo("Lun. 1er → mar. 2 mars");
    }
}
