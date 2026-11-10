package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.RecordingMailTransport;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.benevole.AccountDeletionService;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.Duration;
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
 * Confirmations aux bénévoles (cahier des charges §6, S5a) : les inscriptions et désinscriptions d'un bénévole, par
 * lui-même ou par un coordinateur, se regroupent dans un seul e-mail, qui part 10 minutes après le dernier changement
 * et donne l'état final. Rien pour un bénévole sans adresse ni pour un compte supprimé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class ConfirmationMailTest {

    /** Mardi 12, mercredi 13 et jeudi 14 janvier 2027 : à plus de 48 h de l'horloge de test (lundi 4 à 10 h). */
    private static final LocalDate TUESDAY = LocalDate.of(2027, 1, 12);
    private static final LocalDate WEDNESDAY = LocalDate.of(2027, 1, 13);
    private static final LocalDate THURSDAY = LocalDate.of(2027, 1, 14);
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

    @Autowired
    RegistrationService registrations;

    @Autowired
    AccountDeletionService deletion;

    private TestVolunteers volunteers;

    @BeforeEach
    void setUp() {
        volunteers = new TestVolunteers(mockMvc, sender, coordinators, jdbc);
        jdbc.update("DELETE FROM mail_delivery");
        mails.clear();
    }

    @AfterEach
    void clean() {
        jdbc.update("UPDATE season SET coordinators_contact = NULL");
        jdbc.update("DELETE FROM mail_delivery");
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        for (var date : new LocalDate[] {TUESDAY, WEDNESDAY, THURSDAY}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    private void after(Duration duration) {
        clock.advance(duration);
        jobs.tick();
    }

    /** Page de confirmation, puis POST : ce que fait le bouton « Me désinscrire ». */
    private static void cancel(Browser browser, LocalDate eveningDate, String choice) throws Exception {
        browser.get("/mes-inscriptions/annuler?soiree=" + eveningDate + "&choix=" + choice);
        assertThat(browser.post("/mes-inscriptions/annuler", "soiree", eveningDate.toString(), "choix", choice)
                .getRedirectedUrl()).isEqualTo("/mes-inscriptions");
    }

    private String status(UUID volunteerId) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN sent_at IS NOT NULL THEN 'SENT' WHEN abandoned_at IS NOT NULL THEN abandon_reason
                    ELSE 'PENDING' END
                FROM mail_delivery WHERE kind = 'CONFIRMATION' AND volunteer_id = ? ORDER BY created_at DESC LIMIT 1
                """, String.class, volunteerId);
    }

    @Test
    void a_registration_is_confirmed_ten_minutes_later(CapturedOutput output) throws Exception {
        var anne = volunteers.volunteer();

        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        after(DELAY.minusSeconds(1));
        assertThat(mails.sentTo(anne.email())).isEmpty();
        after(Duration.ofSeconds(1));

        assertThat(mails.sentTo(anne.email())).singleElement().satisfies(mail -> {
            assertThat(mail.to()).containsExactly(anne.email());
            assertThat(mail.subject()).isEqualTo("Vos inscriptions — Hiver Solidaire");
            assertThat(mail.text())
                    .contains("Votre inscription est enregistrée :")
                    .contains("Dîner et nuit du mar. 12 janv. : Passer la nuit et partager le dîner")
                    .contains("Du dîner (19 h) au petit-déjeuner du lendemain")
                    .contains("Pour vous désinscrire, jusqu’au lun. 11 janv. à 23 h 59 :")
                    .contains("http://localhost:8080/mes-inscriptions/annuler?soiree=2027-01-12&choix=NIGHT_AND_DINNER")
                    .contains("http://localhost:8080/mes-inscriptions")
                    .doesNotContain("Enregistré par un coordinateur");
            assertThat(mail.html())
                    .contains("<strong>Dîner et nuit du mar. 12 janv.</strong>")
                    .contains("href=\"http://localhost:8080/mes-inscriptions/annuler?soiree=2027-01-12&amp;choix=NIGHT_AND_DINNER\"");
        });
        assertThat(status(anne.id())).isEqualTo("SENT");
        after(Duration.ofHours(1));
        assertThat(mails.sentTo(anne.email())).hasSize(1);
        assertThat(output).doesNotContain(anne.email());
    }

    @Test
    void registrations_close_together_make_a_single_mail() throws Exception {
        var anne = volunteers.volunteer();

        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        clock.advance(Duration.ofMinutes(6));
        TestVolunteers.register(anne.browser(), WEDNESDAY, "PREPARE_AND_SHARE.SIDES");
        clock.advance(Duration.ofMinutes(6));
        TestVolunteers.register(anne.browser(), THURSDAY, "BREAKFAST");
        after(DELAY.minusMinutes(1));
        assertThat(mails.sentTo(anne.email())).isEmpty();
        after(Duration.ofMinutes(1));

        assertThat(mails.sentTo(anne.email())).singleElement().satisfies(mail -> assertThat(mail.text())
                .contains("Vos inscriptions pour Hiver Solidaire sont enregistrées :")
                .containsSubsequence("Dîner et nuit du mar. 12 janv.", "Dîner du mer. 13 janv.",
                        "Petit-déjeuner du ven. 15 janv.")
                .contains("Repas livré pour 19 h, dîner de 19 h à 21 h – 21 h 30.")
                // Vendredi : 7 h.
                .contains("Petit-déjeuner à 7 h."));
    }

    @Test
    void a_registration_cancelled_before_the_mail_leaves_sends_nothing() throws Exception {
        var anne = volunteers.volunteer();

        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        clock.advance(Duration.ofMinutes(3));
        cancel(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        after(DELAY);

        assertThat(mails.sentTo(anne.email())).isEmpty();
        assertThat(status(anne.id())).isEqualTo("NOTHING_TO_SEND");
    }

    @Test
    void a_cancellation_after_the_mail_left_is_confirmed_in_a_new_mail() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        TestVolunteers.register(anne.browser(), TUESDAY, "BREAKFAST");
        after(DELAY);

        cancel(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        after(DELAY);

        assertThat(mails.sentTo(anne.email())).hasSize(2);
        assertThat(mails.sentTo(anne.email()).getLast().text())
                .contains("Cette inscription est annulée :")
                .contains("Dîner et nuit du mar. 12 janv. : Passer la nuit et partager le dîner")
                .doesNotContain("Petit-déjeuner du mer. 13 janv.")
                .doesNotContain("enregistrée");
    }

    @Test
    void coordinator_actions_are_confirmed_with_a_note() {
        var volunteerId = withEmail();
        var email = jdbc.queryForObject("SELECT email FROM volunteer WHERE id = ?", String.class, volunteerId);

        var registered = registrations.registerByCoordinator(volunteerId,
                RegistrationChoice.onEvening(TUESDAY, RegistrationType.NIGHT_AND_DINNER, null));
        after(DELAY);
        registrations.moveByCoordinator(registered.registrationId(), WEDNESDAY);
        after(DELAY);

        assertThat(mails.sentTo(email)).hasSize(2);
        assertThat(mails.sentTo(email).getFirst().text())
                .contains("Dîner et nuit du mar. 12 janv.")
                .contains("Enregistré par un coordinateur.");
        // Un déplacement : une annulation et une inscription, dans le même e-mail.
        assertThat(mails.sentTo(email).getLast().text())
                .containsSubsequence("Votre inscription est enregistrée :", "Dîner et nuit du mer. 13 janv.",
                        "Enregistré par un coordinateur.", "Cette inscription est annulée :",
                        "Dîner et nuit du mar. 12 janv.", "Annulé par un coordinateur.");
    }

    @Test
    void a_cancellation_by_a_coordinator_is_confirmed() {
        var volunteerId = withEmail();
        var email = jdbc.queryForObject("SELECT email FROM volunteer WHERE id = ?", String.class, volunteerId);
        var registered = registrations.registerByCoordinator(volunteerId,
                RegistrationChoice.onEvening(TUESDAY, RegistrationType.BREAKFAST, null));
        after(DELAY);

        registrations.cancelByCoordinator(registered.registrationId());
        after(DELAY);

        assertThat(mails.sentTo(email).getLast().text())
                .contains("Cette inscription est annulée :")
                .contains("Petit-déjeuner du mer. 13 janv. : Fournir et partager le petit-déjeuner")
                .contains("Annulé par un coordinateur.");
    }

    @Test
    void a_volunteer_without_email_gets_nothing() {
        var volunteerId = withoutEmail();

        registrations.registerByCoordinator(volunteerId,
                RegistrationChoice.onEvening(TUESDAY, RegistrationType.NIGHT_AND_DINNER, null));
        after(DELAY);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_delivery WHERE volunteer_id = ?", Integer.class,
                volunteerId)).isZero();
    }

    @Test
    void a_deleted_account_gets_nothing() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");

        deletion.delete(anne.id());
        after(DELAY);

        assertThat(mails.sentTo(anne.email())).isEmpty();
        assertThat(status(anne.id())).isEqualTo("NO_RECIPIENT");
    }

    @Test
    void the_cancellation_link_only_opens_the_confirmation_page() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        after(DELAY);
        var link = mails.sentTo(anne.email()).getFirst().text().lines()
                .filter(line -> line.contains("/mes-inscriptions/annuler")).findFirst().orElseThrow().strip();

        var page = anne.browser().get(link.replace("http://localhost:8080", ""));

        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getContentAsString()).contains("Confirmer ma désinscription");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registration WHERE volunteer_id = ? AND cancelled_at IS NULL
                """, Integer.class, anne.id())).isEqualTo(1);
    }

    @Test
    void the_reply_to_header_is_the_coordinators_address() throws Exception {
        jdbc.update("UPDATE season SET coordinators_contact = ?", "coordination@example.org");
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        after(DELAY);

        assertThat(mails.sentTo(anne.email()).getFirst().replyTo()).isEqualTo("coordination@example.org");
        assertThat(mails.sentTo(anne.email()).getFirst().text())
                .contains("Une question ? Répondez simplement à cet e-mail, il arrivera aux coordinateurs "
                        + "(coordination@example.org).");
    }

    @Test
    void a_coordinators_phone_gives_no_reply_to_but_the_number() throws Exception {
        jdbc.update("UPDATE season SET coordinators_contact = ?", "01 99 00 12 34");
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        after(DELAY);

        assertThat(mails.sentTo(anne.email()).getFirst().replyTo()).isNull();
        assertThat(mails.sentTo(anne.email()).getFirst().text())
                .contains("Une question ? Appelez les coordinateurs au 01 99 00 12 34.");
    }

    @Test
    void without_a_coordinators_contact_there_is_no_reply_to() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        after(DELAY);

        assertThat(mails.sentTo(anne.email()).getFirst().replyTo()).isNull();
        assertThat(mails.sentTo(anne.email()).getFirst().text()).contains("Une question ? Contactez un coordinateur.");
    }

    /** Bénévole au profil complet avec adresse, créé directement en base (inscrit ensuite par un coordinateur). */
    private UUID withEmail() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, profile_completed_at)
                VALUES (?, ?, 'Martin', 'Paul', '+33600000002', ?, now())
                """, id, "paul-" + id + "@example.org", "P" + id.toString().substring(0, 8));
        return id;
    }

    /** Bénévole inscrit par téléphone : profil complet, sans adresse (V11). */
    private UUID withoutEmail() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, profile_completed_at)
                VALUES (?, NULL, 'Durand', 'Jeanne', '+33600000003', ?, now())
                """, id, "J" + id.toString().substring(0, 8));
        return id;
    }
}
