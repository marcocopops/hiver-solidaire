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
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
 * Rappel de la veille à 18 h (cahier des charges §6, S5a) : le dimanche 10 janvier 2027 à 18 h, chaque bénévole reçoit
 * un e-mail qui reprend la carte du lundi 11 (ADR-0009), c'est-à-dire le petit-déjeuner du lundi matin (soirée du
 * dimanche) puis le dîner et la nuit du lundi soir. Un seul envoi, même après un redémarrage ; rattrapage jusqu'à 22 h.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class ReminderMailTest {

    /** Soirée du dimanche 10 janvier : son petit-déjeuner est sur la carte du lundi 11. */
    private static final LocalDate SUNDAY = LocalDate.of(2027, 1, 10);
    /** Soirée du lundi 11 janvier : son dîner et sa nuit sont sur la carte du lundi 11. */
    private static final LocalDate MONDAY = LocalDate.of(2027, 1, 11);
    private static final String SUBJECT = "Rappel : demain, lun. 11 janv. — Hiver Solidaire";

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
    MailPlanner planner;

    @Autowired
    MailDispatcher dispatcher;

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
        mails.clear();
        jdbc.update("DELETE FROM mail_delivery");
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        for (var date : new LocalDate[] {SUNDAY, MONDAY, MONDAY.plusDays(1)}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    private void at(int day, int hour, int minute) {
        clock.set(LocalDateTime.of(2027, 1, day, hour, minute).atZone(ParisTime.ZONE).toInstant());
        jobs.tick();
    }

    private List<String> remindersTo(String email) {
        return mails.sentTo(email).stream().filter(mail -> mail.subject().startsWith("Rappel")).map(mail -> mail.text())
                .toList();
    }

    @Test
    void the_reminder_lists_the_card_of_tomorrow_at_six_pm(CapturedOutput output) throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), MONDAY, "NIGHT_AND_DINNER");
        TestVolunteers.register(anne.browser(), SUNDAY, "BREAKFAST");

        at(10, 17, 59);
        assertThat(remindersTo(anne.email())).isEmpty();
        at(10, 18, 0);

        assertThat(mails.sentTo(anne.email()).stream().filter(mail -> mail.subject().equals(SUBJECT)))
                .singleElement().satisfies(mail -> {
                    assertThat(mail.text())
                            .contains("Rappel : demain, lun. 11 janv., vous participez à l’accueil Hiver Solidaire.")
                            // Le petit-déjeuner du lundi matin d'abord, rattaché à la soirée du dimanche : sa limite
                            // de désinscription (samedi 23 h 59) est passée.
                            .containsSubsequence("Petit-déjeuner du lun. 11 janv.", "Petit-déjeuner à 7 h.",
                                    "Si vous ne pouvez plus venir, prévenez les coordinateurs.",
                                    "Dîner et nuit du lun. 11 janv.",
                                    "Pour vous désinscrire, jusqu’au dim. 10 janv. à 23 h 59 :",
                                    "http://localhost:8080/mes-inscriptions/annuler?soiree=2027-01-11&choix=NIGHT_AND_DINNER")
                            .doesNotContain("soiree=2027-01-10");
                    assertThat(mail.html()).contains("Petit-déjeuner du lun. 11 janv.");
                });
        assertThat(output).doesNotContain(anne.email());
    }

    @Test
    void only_people_on_tomorrows_card_are_reminded() throws Exception {
        var today = volunteers.volunteer();
        var dayAfter = volunteers.volunteer();
        // Dîner du dimanche : rappelé le samedi. Petit-déjeuner du mardi : rappelé le lundi.
        TestVolunteers.register(today.browser(), SUNDAY, "NIGHT_AND_DINNER");
        TestVolunteers.register(dayAfter.browser(), MONDAY, "BREAKFAST");

        at(10, 18, 0);

        assertThat(remindersTo(today.email())).isEmpty();
        assertThat(remindersTo(dayAfter.email())).isEmpty();
    }

    @Test
    void the_reminder_is_sent_once_even_after_a_restart() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), MONDAY, "NIGHT_AND_DINNER");

        at(10, 18, 0);
        at(10, 18, 30);
        // Redémarrage : une nouvelle tâche, sans rien en mémoire, repart de la table d'envois.
        new MailJobs(planner, dispatcher).tick();
        at(10, 21, 0);

        assertThat(remindersTo(anne.email())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_delivery WHERE kind = 'REMINDER' AND volunteer_id = ?",
                Integer.class, anne.id())).isEqualTo(1);
    }

    @Test
    void a_registration_cancelled_before_six_pm_is_not_reminded() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), MONDAY, "NIGHT_AND_DINNER");
        clock.set(LocalDateTime.of(2027, 1, 10, 17, 0).atZone(ParisTime.ZONE).toInstant());
        cancel(anne.browser(), MONDAY, "NIGHT_AND_DINNER");

        at(10, 18, 0);

        assertThat(remindersTo(anne.email())).isEmpty();
    }

    @Test
    void a_missed_reminder_is_caught_up_until_ten_pm() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), MONDAY, "NIGHT_AND_DINNER");

        // L'appli était arrêtée à 18 h ; elle redémarre à 21 h 30.
        at(10, 21, 30);

        assertThat(remindersTo(anne.email())).hasSize(1);
    }

    @Test
    void a_reminder_is_never_sent_after_ten_pm() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), MONDAY, "NIGHT_AND_DINNER");

        // L'appli était arrêtée de 18 h à 22 h 01 : le rappel est perdu, jamais envoyé la nuit ni le jour même.
        at(10, 22, 1);
        at(11, 7, 0);

        assertThat(remindersTo(anne.email())).isEmpty();
    }

    @Test
    void neither_a_volunteer_without_email_nor_a_deleted_account_is_reminded() throws Exception {
        var phoneOnly = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, profile_completed_at)
                VALUES (?, NULL, 'Durand', 'Jeanne', '+33600000003', ?, now())
                """, phoneOnly, "J" + phoneOnly.toString().substring(0, 8));
        registrations.registerByCoordinator(phoneOnly,
                RegistrationChoice.onEvening(MONDAY, RegistrationType.NIGHT_AND_DINNER, null));
        var gone = volunteers.volunteer();
        TestVolunteers.register(gone.browser(), MONDAY, "PREPARE_AND_SHARE.MAIN_DISH");
        deletion.delete(gone.id());

        at(10, 18, 0);

        assertThat(remindersTo(gone.email())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_delivery WHERE kind = 'REMINDER'", Integer.class))
                .isZero();
    }

    private static void cancel(Browser browser, LocalDate eveningDate, String choice) throws Exception {
        browser.get("/mes-inscriptions/annuler?soiree=" + eveningDate + "&choix=" + choice);
        assertThat(browser.post("/mes-inscriptions/annuler", "soiree", eveningDate.toString(), "choix", choice)
                .getRedirectedUrl()).isEqualTo("/mes-inscriptions");
    }
}
