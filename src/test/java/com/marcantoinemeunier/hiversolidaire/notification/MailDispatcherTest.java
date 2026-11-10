package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.RecordingMailTransport;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Échecs d'envoi (Brevo indisponible, quota atteint) : l'inscription réussit quand même, l'e-mail est réessayé plus tard
 * ({@link MailDispatcher#RETRY_DELAYS}), au plus {@value MailDispatcher#MAX_ATTEMPTS} fois et jamais après sa limite.
 * Les logs ne donnent que des identifiants techniques, jamais l'adresse que contient le message de l'exception.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class MailDispatcherTest {

    private static final LocalDate TUESDAY = LocalDate.of(2027, 1, 12);
    private static final Duration DELAY = Duration.ofMinutes(10);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    RecordingMailTransport mails;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    MailJobs jobs;

    private TestVolunteers volunteers;

    @BeforeEach
    void setUp() {
        volunteers = new TestVolunteers(mockMvc, sender, coordinators, jdbc);
        jdbc.update("DELETE FROM mail_delivery");
        mails.clear();
    }

    @AfterEach
    void clean() {
        mails.clear();
        jdbc.update("DELETE FROM mail_delivery");
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                TUESDAY);
    }

    private void after(Duration duration) {
        clock.advance(duration);
        jobs.tick();
    }

    private record Row(int attempts, Instant nextAttemptAt, Instant sentAt, String abandonReason) {
    }

    private Row row(UUID volunteerId) {
        return jdbc.queryForObject("""
                SELECT attempts, next_attempt_at, sent_at, abandon_reason FROM mail_delivery
                WHERE kind = 'CONFIRMATION' AND volunteer_id = ?
                """, (rs, index) -> new Row(rs.getInt("attempts"), rs.getTimestamp("next_attempt_at").toInstant(),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                rs.getString("abandon_reason")), volunteerId);
    }

    @Test
    void a_failed_send_is_retried_later_without_the_address_in_the_logs(CapturedOutput output) throws Exception {
        mails.failNext(1);
        var anne = volunteers.volunteer();
        // L'inscription ne dépend pas de l'envoi : elle réussit.
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");

        after(DELAY);

        assertThat(mails.sentTo(anne.email())).isEmpty();
        assertThat(row(anne.id()).attempts()).isEqualTo(1);
        assertThat(row(anne.id()).nextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
        assertThat(output).contains("failed, attempt 1 (MailSendException)").doesNotContain(anne.email());

        after(Duration.ofSeconds(59));
        assertThat(mails.sentTo(anne.email())).isEmpty();
        after(Duration.ofSeconds(1));
        assertThat(mails.sentTo(anne.email())).hasSize(1);
        assertThat(row(anne.id()).sentAt()).isEqualTo(clock.instant());
        assertThat(output).doesNotContain(anne.email()).doesNotContain("Martin").doesNotContain("0600000001");
    }

    @Test
    void a_mail_is_abandoned_after_six_failures(CapturedOutput output) throws Exception {
        mails.failNext(100);
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");

        after(DELAY);
        for (var delay : MailDispatcher.RETRY_DELAYS) {
            after(delay);
        }

        assertThat(row(anne.id()).attempts()).isEqualTo(MailDispatcher.MAX_ATTEMPTS);
        assertThat(row(anne.id()).abandonReason()).isEqualTo("FAILED");
        after(Duration.ofHours(2));
        assertThat(row(anne.id()).attempts()).isEqualTo(MailDispatcher.MAX_ATTEMPTS);
        assertThat(output).contains("abandoned after 6 failed attempts").doesNotContain(anne.email());
    }

    @Test
    void a_mail_is_never_sent_after_its_limit() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");

        // L'appli reste arrêtée plus de 48 h après l'heure d'envoi prévue.
        after(DELAY.plus(MailOutbox.CONFIRMATION_LIFETIME).plusSeconds(1));

        assertThat(mails.sentTo(anne.email())).isEmpty();
        assertThat(row(anne.id()).abandonReason()).isEqualTo("EXPIRED");
    }
}
