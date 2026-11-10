package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Période des exports : 7 prochains jours par défaut, 31 jours au plus, bornes comprises. */
class ExportPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 1, 4);

    @Test
    void without_dates_the_period_is_the_next_7_days() {
        var period = ExportPeriod.orDefault(null, null, TODAY);

        assertThat(period.from()).isEqualTo(TODAY);
        assertThat(period.to()).isEqualTo(LocalDate.of(2027, 1, 10));
    }

    @Test
    void a_single_missing_date_is_not_replaced_by_the_default() {
        assertThatThrownBy(() -> ExportPeriod.orDefault("2027-01-12", null, TODAY))
                .isInstanceOf(ExportPeriod.InvalidPeriodException.class);
    }

    @Test
    void valid_dates_are_kept() {
        var period = ExportPeriod.parse("2027-01-12", "2027-01-12");

        assertThat(period.from()).isEqualTo(LocalDate.of(2027, 1, 12));
        assertThat(period.to()).isEqualTo(LocalDate.of(2027, 1, 12));
    }

    @Test
    void up_to_31_days_are_accepted() {
        var period = ExportPeriod.parse("2027-01-01", "2027-01-31");

        assertThat(period.to()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void more_than_31_days_are_refused() {
        assertThatThrownBy(() -> ExportPeriod.parse("2027-01-01", "2027-02-01"))
                .isInstanceOf(ExportPeriod.InvalidPeriodException.class)
                .extracting("messageKey").isEqualTo("admin.exports.error.tooLong");
    }

    @Test
    void an_end_before_the_start_is_refused() {
        assertThatThrownBy(() -> ExportPeriod.parse("2027-01-10", "2027-01-04"))
                .isInstanceOf(ExportPeriod.InvalidPeriodException.class)
                .extracting("messageKey").isEqualTo("admin.exports.error.order");
    }

    @Test
    void malformed_or_missing_dates_are_refused() {
        for (var pair : new String[][] {{"hier", "2027-01-04"}, {"2027-01-04", ""}, {"2027-02-30", "2027-03-01"},
                {null, "2027-01-04"}, {"2027-01-04", null}}) {
            assertThatThrownBy(() -> ExportPeriod.parse(pair[0], pair[1])).as(pair[0] + " " + pair[1])
                    .isInstanceOf(ExportPeriod.InvalidPeriodException.class)
                    .extracting("messageKey").isEqualTo("admin.exports.error.date");
        }
    }
}
