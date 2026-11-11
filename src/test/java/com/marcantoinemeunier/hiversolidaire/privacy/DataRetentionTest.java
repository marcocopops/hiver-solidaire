package com.marcantoinemeunier.hiversolidaire.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Purge quotidienne (cahier des charges §7, ADR-0014) avec l'horloge réglable, chaque durée vérifiée la veille (rien) et
 * le jour même (fait). Saison de référence : la saison d'exemple, dernière soirée le samedi 20 mars 2027, donc purge des
 * bénévoles sans accord le 21 avril 2027. Base isolée, neuve à chaque test : la purge touche toute la base, et ces tests
 * avancent l'horloge de plusieurs années.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@TestPropertySource(properties = "test.isolated-database=retention")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ExtendWith(OutputCaptureExtension.class)
class DataRetentionTest {

    private static final LocalDate PAST_EVENING = LocalDate.of(2027, 3, 10);

    @Autowired
    DataRetention retention;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
    }

    private static Instant paris(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ParisTime.ZONE).toInstant();
    }

    private DataRetention.Report runAt(Instant now) {
        clock.set(now);
        return retention.run();
    }

    /** Bénévole au profil complet, données factices. */
    private UUID volunteer(String email, boolean consent, Instant createdAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, recontact_next_season, created_at,
                    updated_at, profile_completed_at)
                VALUES (?, ?, 'Martin', 'Anne', '+33600000001', ?, ?, ?, ?, ?)
                """, id, email, "Test" + id.toString().substring(0, 8), consent, ts(createdAt), ts(createdAt),
                ts(createdAt));
        return id;
    }

    /** Bénévole inscrit par téléphone par un coordinateur : sans adresse, sans accord. */
    private UUID phoneOnly(Instant createdAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, last_name, first_name, phone, pseudo, created_at, updated_at, profile_completed_at)
                VALUES (?, 'Durand', 'Paul', '+33600000002', ?, ?, ?, ?)
                """, id, "Tel" + id.toString().substring(0, 8), ts(createdAt), ts(createdAt), ts(createdAt));
        return id;
    }

    private UUID withoutProfile(String email, Instant createdAt) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email, created_at, updated_at) VALUES (?, ?, ?, ?)", id, email,
                ts(createdAt), ts(createdAt));
        return id;
    }

    private void register(UUID volunteerId, LocalDate eveningDate, boolean cancelled) {
        jdbc.update("""
                INSERT INTO registration (id, evening_id, type, volunteer_id, created_at, cancelled_at)
                VALUES (?, (SELECT id FROM evening WHERE evening_date = ?), 'NIGHT_AND_DINNER', ?, ?, ?)
                """, UUID.randomUUID(), eveningDate, volunteerId, ts(clock.instant()),
                cancelled ? ts(clock.instant()) : null);
    }

    private void session(UUID volunteerId) {
        var expiry = System.currentTimeMillis() + Duration.ofDays(30).toMillis();
        jdbc.update("""
                INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time, max_inactive_interval,
                    expiry_time, principal_name)
                VALUES (?, ?, ?, ?, 2592000, ?, ?)
                """, UUID.randomUUID().toString(), UUID.randomUUID().toString(), System.currentTimeMillis(),
                System.currentTimeMillis(), expiry, volunteerId.toString());
    }

    private void loginToken(String email) {
        jdbc.update("INSERT INTO one_time_token (token_hash, username, expires_at) VALUES (?, ?, ?)",
                "a".repeat(32) + UUID.randomUUID().toString().replace("-", ""), email,
                ts(Instant.now().plus(Duration.ofDays(1))));
    }

    private boolean anonymized(UUID volunteerId) {
        return jdbc.queryForObject("""
                SELECT deleted_at IS NOT NULL AND email IS NULL AND last_name IS NULL AND first_name IS NULL
                    AND phone IS NULL AND pseudo IS NULL
                FROM volunteer WHERE id = ?
                """, Boolean.class, volunteerId);
    }

    private boolean exists(UUID volunteerId) {
        return jdbc.queryForObject("SELECT count(*) FROM volunteer WHERE id = ?", Integer.class, volunteerId) == 1;
    }

    private int activeRegistrations(LocalDate eveningDate) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.cancelled_at IS NULL
                """, Integer.class, eveningDate);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    @Test
    void without_consent_volunteers_are_anonymized_one_month_after_the_season() {
        var anne = volunteer("anne-retention@example.org", false, paris(2026, 12, 20, 10, 0));
        var paul = phoneOnly(paris(2027, 1, 5, 10, 0));
        var claire = volunteer("claire-retention@example.org", true, paris(2026, 12, 20, 10, 0));
        register(anne, PAST_EVENING, false);
        register(paul, PAST_EVENING.plusDays(1), false);
        session(anne);
        loginToken("anne-retention@example.org");

        assertThat(runAt(paris(2027, 4, 20, 23, 59)).withoutConsent()).isZero();
        assertThat(anonymized(anne)).isFalse();

        var report = runAt(paris(2027, 4, 21, 0, 0));

        assertThat(report.withoutConsent()).isEqualTo(2);
        assertThat(anonymized(anne)).isTrue();
        assertThat(anonymized(paul)).isTrue();
        assertThat(anonymized(claire)).isFalse();
        // L'historique reste compté, rattaché à personne (ADR-0008).
        assertThat(activeRegistrations(PAST_EVENING)).isEqualTo(1);
        assertThat(activeRegistrations(PAST_EVENING.plusDays(1))).isEqualTo(1);
        assertThat(count("spring_session")).isZero();
        assertThat(count("one_time_token")).isZero();
    }

    @Test
    void volunteers_of_the_next_season_or_who_joined_after_the_end_are_kept() {
        // Saison suivante déjà créée, avec une inscription.
        var next = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO season (id, first_evening, last_evening, registrations_open_at, sample)
                VALUES (?, DATE '2027-12-06', DATE '2028-03-19', TIMESTAMPTZ '2027-11-15 00:00:00 Europe/Paris', false)
                """, next);
        jdbc.update("INSERT INTO evening (id, season_id, evening_date) VALUES (?, ?, DATE '2027-12-10')",
                UUID.randomUUID(), next);
        var bruno = volunteer("bruno-retention@example.org", false, paris(2027, 1, 2, 10, 0));
        register(bruno, PAST_EVENING, false);
        register(bruno, LocalDate.of(2027, 12, 10), false);
        var late = volunteer("tardif-retention@example.org", false, paris(2027, 3, 25, 10, 0));

        var report = runAt(paris(2027, 4, 21, 4, 30));

        assertThat(report.withoutConsent()).isZero();
        assertThat(anonymized(bruno)).isFalse();
        assertThat(anonymized(late)).isFalse();
    }

    @Test
    void with_consent_volunteers_are_kept_until_three_years_without_participation() {
        var claire = volunteer("claire-retention@example.org", true, paris(2026, 12, 20, 10, 0));
        register(claire, PAST_EVENING, false);
        // Une inscription annulée n'est pas une participation.
        register(claire, LocalDate.of(2027, 3, 18), true);
        var denis = volunteer("denis-retention@example.org", true, paris(2027, 2, 1, 10, 0));

        assertThat(runAt(paris(2030, 1, 31, 23, 59)).inactiveWithConsent()).isZero();
        assertThat(runAt(paris(2030, 2, 1, 0, 0)).inactiveWithConsent()).isEqualTo(1);
        assertThat(anonymized(denis)).isTrue();
        assertThat(anonymized(claire)).isFalse();

        assertThat(runAt(paris(2030, 3, 9, 23, 59)).inactiveWithConsent()).isZero();
        assertThat(runAt(paris(2030, 3, 10, 0, 0)).inactiveWithConsent()).isEqualTo(1);
        assertThat(anonymized(claire)).isTrue();
        assertThat(activeRegistrations(PAST_EVENING)).isEqualTo(1);
    }

    @Test
    void withdrawing_consent_after_the_season_anonymizes_at_the_next_run() {
        var claire = volunteer("claire-retention@example.org", true, paris(2026, 12, 20, 10, 0));
        register(claire, PAST_EVENING, false);

        assertThat(runAt(paris(2027, 5, 1, 4, 30)).total()).isZero();
        jdbc.update("UPDATE volunteer SET recontact_next_season = false WHERE id = ?", claire);

        assertThat(runAt(paris(2027, 5, 2, 4, 30)).withoutConsent()).isEqualTo(1);
        assertThat(anonymized(claire)).isTrue();
    }

    @Test
    void an_account_without_profile_is_deleted_after_seven_days() {
        var created = paris(2027, 1, 4, 10, 0);
        var email = "sans-profil-retention@example.org";
        var ghost = withoutProfile(email, created);
        session(ghost);
        loginToken(email);

        assertThat(runAt(created.plus(Duration.ofDays(7)).minusSeconds(60)).incompleteAccounts()).isZero();
        assertThat(exists(ghost)).isTrue();

        assertThat(runAt(created.plus(Duration.ofDays(7))).incompleteAccounts()).isEqualTo(1);
        assertThat(exists(ghost)).isFalse();
        assertThat(count("spring_session")).isZero();
        assertThat(count("one_time_token")).isZero();
    }

    @Test
    void technical_data_goes_after_six_months() {
        var created = paris(2027, 1, 4, 10, 0);
        clock.set(created);
        jdbc.update("""
                INSERT INTO mail_delivery (id, kind, reference_date, created_at, closed_at, next_attempt_at, not_after, sent_at)
                VALUES (?, 'DAILY_RECAP', DATE '2027-01-04', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ts(created), ts(created), ts(created), ts(created.plusSeconds(3600)),
                ts(created));
        jdbc.update("""
                INSERT INTO mail_delivery (id, kind, reference_date, created_at, closed_at, next_attempt_at, not_after,
                    abandoned_at, abandon_reason)
                VALUES (?, 'DAILY_RECAP', DATE '2027-01-05', ?, ?, ?, ?, ?, 'EXPIRED')
                """, UUID.randomUUID(), ts(created), ts(created), ts(created), ts(created.plusSeconds(3600)),
                ts(created));
        var alert = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO coordinator_alert (id, kind, evening_id, created_at)
                VALUES (?, 'EVENING_INCOMPLETE', (SELECT id FROM evening WHERE evening_date = DATE '2027-01-06'), ?)
                """, alert, ts(created));
        jdbc.update("""
                INSERT INTO mail_delivery (id, kind, alert_id, created_at, closed_at, next_attempt_at, not_after, sent_at)
                VALUES (?, 'ALERT', ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), alert, ts(created), ts(created), ts(created), ts(created.plusSeconds(3600)),
                ts(created));
        var before = runAt(paris(2027, 7, 4, 9, 59));
        assertThat(before.mailDeliveries()).isZero();
        assertThat(before.alerts()).isZero();
        assertThat(count("mail_delivery")).isEqualTo(3);

        // Journal d'audit : la base n'accepte de supprimer que ce qui a plus de six mois à son horloge à elle.
        var coordinator = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_log (id, occurred_at, coordinator_id, action, alert_id)
                VALUES (?, now() - interval '7 months', ?, 'ALERT_HANDLED', ?),
                       (?, now() - interval '5 months', ?, 'ALERT_HANDLED', ?)
                """, UUID.randomUUID(), coordinator, alert, UUID.randomUUID(), coordinator, alert);

        var report = runAt(paris(2027, 7, 4, 10, 0));

        assertThat(report.mailDeliveries()).isEqualTo(3);
        assertThat(report.alerts()).isEqualTo(1);
        assertThat(report.auditEntries()).isEqualTo(1);
        assertThat(count("mail_delivery")).isZero();
        assertThat(count("coordinator_alert")).isZero();
        assertThat(count("audit_log")).isEqualTo(1);
    }

    @Test
    void a_second_run_on_the_same_day_does_nothing_more(CapturedOutput output) {
        var anne = volunteer("anne-retention@example.org", false, paris(2026, 12, 20, 10, 0));
        register(anne, PAST_EVENING, false);
        withoutProfile("sans-profil-retention@example.org", paris(2027, 4, 1, 10, 0));

        var first = runAt(paris(2027, 4, 21, 4, 30));
        var second = runAt(paris(2027, 4, 21, 4, 31));

        assertThat(first.withoutConsent()).isEqualTo(1);
        assertThat(first.incompleteAccounts()).isEqualTo(1);
        assertThat(second.total()).isZero();
        assertThat(activeRegistrations(PAST_EVENING)).isEqualTo(1);
        // Une ligne par exécution, des comptes seulement.
        assertThat(output).contains("Data retention run: 1 incomplete account(s) deleted, 1 anonymized without consent")
                .doesNotContain("retention@example.org").doesNotContain("Martin").doesNotContain("+33600000001")
                .doesNotContain(anne.toString());
    }
}
