package com.marcantoinemeunier.hiversolidaire.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Durées de conservation (cahier des charges §7, ADR-0014), à la date de Paris, sans jour d'avance ni de retard. */
class RetentionPolicyTest {

    private static Instant paris(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ParisTime.ZONE).toInstant();
    }

    @Test
    void without_consent_data_goes_one_month_after_the_end_of_the_season() {
        // Dernière soirée le samedi 20 mars 2027 : la saison finit au petit-déjeuner du 21, la purge vient le 21 avril.
        var lastEvening = LocalDate.of(2027, 3, 20);

        assertThat(RetentionPolicy.withoutConsentFrom(lastEvening)).isEqualTo(LocalDate.of(2027, 4, 21));
    }

    @Test
    void the_reference_season_is_the_latest_one_whose_delay_has_passed() {
        var seasons = List.of(LocalDate.of(2027, 3, 20), LocalDate.of(2028, 3, 25), LocalDate.of(2026, 3, 22));

        assertThat(RetentionPolicy.seasonToPurge(seasons, LocalDate.of(2027, 4, 20)))
                .contains(LocalDate.of(2026, 3, 22));
        assertThat(RetentionPolicy.seasonToPurge(seasons, LocalDate.of(2027, 4, 21)))
                .contains(LocalDate.of(2027, 3, 20));
        assertThat(RetentionPolicy.seasonToPurge(seasons, LocalDate.of(2028, 4, 26)))
                .contains(LocalDate.of(2028, 3, 25));
        assertThat(RetentionPolicy.seasonToPurge(List.of(LocalDate.of(2027, 3, 20)), LocalDate.of(2027, 4, 20)))
                .isEqualTo(Optional.empty());
    }

    @Test
    void with_consent_data_goes_after_three_years_without_participation() {
        var lastParticipation = LocalDate.of(2027, 3, 20);

        assertThat(RetentionPolicy.inactiveWithConsent(lastParticipation, LocalDate.of(2030, 3, 19))).isFalse();
        assertThat(RetentionPolicy.inactiveWithConsent(lastParticipation, LocalDate.of(2030, 3, 20))).isTrue();
        // 29 février : trois ans plus tard, le 28 février.
        assertThat(RetentionPolicy.inactiveWithConsent(LocalDate.of(2028, 2, 29), LocalDate.of(2031, 2, 27)))
                .isFalse();
        assertThat(RetentionPolicy.inactiveWithConsent(LocalDate.of(2028, 2, 29), LocalDate.of(2031, 2, 28)))
                .isTrue();
    }

    @Test
    void an_account_without_profile_goes_after_seven_days() {
        var now = paris(2027, 1, 11, 10, 0);

        assertThat(RetentionPolicy.incompleteAccountsCreatedBefore(now)).isEqualTo(paris(2027, 1, 4, 10, 0));
    }

    @Test
    void technical_data_goes_after_six_months_at_paris_time() {
        // Passage à l'heure d'été le 28 mars 2027 : six mois avant le 30 septembre à 10 h, le 30 mars à 10 h (heure de
        // Paris), pas à une heure près.
        assertThat(RetentionPolicy.technicalDataCreatedBefore(paris(2027, 9, 30, 10, 0)))
                .isEqualTo(paris(2027, 3, 30, 10, 0));
        assertThat(RetentionPolicy.technicalDataCreatedBefore(paris(2027, 11, 15, 4, 30)))
                .isEqualTo(paris(2027, 5, 15, 4, 30));
    }
}
