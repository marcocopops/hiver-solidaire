package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.RecordingMailTransport;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestRecipientGuard;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Garde-fou d'envoi (cahier des charges §7, modèle de menaces M30) : en production, aucun e-mail ne part vers un domaine
 * réservé aux tests (RFC 2606, RFC 6761). L'e-mail est abandonné sans erreur ; la demande de lien donne la même page.
 * L'adresse « réelle » de ces tests est un littéral IP de documentation (RFC 5737), non routable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class ReservedDomainGuardTest {

    private static final LocalDate TUESDAY = LocalDate.of(2027, 1, 19);
    private static final String ROUTABLE_LOOKING = "coordination@[192.0.2.1]";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    RecordingMailTransport mails;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    TestRecipientGuard guard;

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
        guard.reset();
        coordinators.remove(ROUTABLE_LOOKING);
        mails.clear();
        jdbc.update("DELETE FROM mail_delivery");
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                TUESDAY);
    }

    @Test
    void a_confirmation_to_a_reserved_domain_is_abandoned_without_error(CapturedOutput output) throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        guard.block();

        clock.advance(Duration.ofMinutes(10));
        jobs.tick();

        assertThat(mails.sentTo(anne.email())).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT abandon_reason FROM mail_delivery WHERE kind = 'CONFIRMATION' AND volunteer_id = ?
                """, String.class, anne.id())).isEqualTo("RESERVED_DOMAIN");
        assertThat(output).contains("(CONFIRMATION) abandoned: RESERVED_DOMAIN").doesNotContain(anne.email());
    }

    @Test
    void a_mail_to_several_recipients_only_goes_to_the_allowed_ones() throws Exception {
        var reserved = "coordination-" + UUID.randomUUID() + "@example.org";
        coordinators.add(reserved);
        coordinators.add(ROUTABLE_LOOKING);
        try {
            guard.block();
            clock.set(LocalDateTime.of(2027, 1, 4, 20, 0).atZone(ParisTime.ZONE).toInstant());

            jobs.tick();

            assertThat(mails.sentTo(ROUTABLE_LOOKING)).singleElement()
                    .satisfies(mail -> assertThat(mail.to()).containsExactly(ROUTABLE_LOOKING));
            assertThat(mails.sentTo(reserved)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_delivery WHERE kind = 'DAILY_RECAP' AND sent_at IS NOT NULL",
                    Integer.class)).isEqualTo(1);
        } finally {
            coordinators.remove(reserved);
        }
    }

    @Test
    void without_the_guard_every_address_receives_its_mail() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");

        clock.advance(Duration.ofMinutes(10));
        jobs.tick();

        assertThat(mails.sentTo(anne.email())).hasSize(1);
    }

    @Test
    void no_login_link_is_sent_to_a_reserved_domain_and_the_page_is_the_same() throws Exception {
        guard.block();
        var reserved = Browser.uniqueEmail();
        var browser = new Browser(mockMvc);
        browser.get("/connexion");

        var response = browser.requestLink(reserved);

        assertThat(response.getRedirectedUrl()).isEqualTo("/connexion/envoye");
        assertThat(sender.sentTo(reserved)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM one_time_token WHERE username = ?", Integer.class,
                reserved)).isZero();
    }
}
