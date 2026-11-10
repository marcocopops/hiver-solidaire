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
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationChoice;
import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Alertes aux coordinateurs (cahier des charges §6, S5a) : « soirée devenue incomplète » part tout de suite par e-mail
 * si le dîner commence dans moins de 48 h ; au-delà, et pour les autres alertes, seulement dans le récap. Une alerte
 * n'est jamais envoyée deux fois. Horloge de test : lundi 4 janvier 2027 à 10 h.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class CoordinatorAlertMailTest {

    /** Mardi 5 janvier : dîner dans 33 h. */
    private static final LocalDate SOON = LocalDate.of(2027, 1, 5);
    /** Jeudi 7 janvier : dîner dans 81 h, mais dans les 7 jours de l'alerte. */
    private static final LocalDate LATER = LocalDate.of(2027, 1, 7);

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

    private TestVolunteers volunteers;
    private String coordinator;

    @BeforeEach
    void setUp() {
        volunteers = new TestVolunteers(mockMvc, sender, coordinators, jdbc);
        coordinator = "coordination-" + UUID.randomUUID() + "@example.org";
        coordinators.add(coordinator);
        jdbc.update("DELETE FROM mail_delivery");
        mails.clear();
    }

    @AfterEach
    void clean() {
        coordinators.remove(coordinator);
        mails.clear();
        jdbc.update("DELETE FROM mail_delivery");
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        for (var date : new LocalDate[] {SOON, LATER}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    /** Une bénévole fournit le petit-déjeuner de la soirée, puis se désinscrit : la soirée perd ce minimum. */
    private void breakfastCancelled(LocalDate eveningDate) throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), eveningDate, "BREAKFAST");
        cancel(anne.browser(), eveningDate, "BREAKFAST");
    }

    private int sentAlerts(LocalDate eveningDate) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id
                WHERE e.evening_date = ? AND a.sent_at IS NOT NULL
                """, Integer.class, eveningDate);
    }

    @Test
    void an_evening_within_48_hours_is_mailed_immediately_and_once() throws Exception {
        breakfastCancelled(SOON);

        jobs.tick();

        assertThat(mails.sentTo(coordinator)).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("Soirée du mar. 5 janv. à compléter — Hiver Solidaire");
            assertThat(mail.to()).contains(coordinator);
            assertThat(mail.replyTo()).isNull();
            assertThat(mail.text())
                    .contains("Après une désinscription, la soirée du mar. 5 janv. n’a plus tous ses minimums.")
                    .contains("- Petit-déjeuner : 1 à pourvoir")
                    .contains("http://localhost:8080/admin/journee?jour=2027-01-05")
                    // Aucun nom de bénévole dans l'alerte.
                    .doesNotContain("Martin").doesNotContain("Anne");
        });
        assertThat(sentAlerts(SOON)).isEqualTo(1);

        clock.advance(Duration.ofHours(1));
        jobs.tick();
        assertThat(mails.sentTo(coordinator)).hasSize(1);
    }

    @Test
    void an_evening_48_hours_or_more_ahead_waits_for_the_recap() throws Exception {
        breakfastCancelled(LATER);

        clock.advance(Duration.ofMinutes(1));
        jobs.tick();

        assertThat(mails.sentTo(coordinator)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id
                WHERE e.evening_date = ? AND a.kind = 'EVENING_INCOMPLETE' AND a.sent_at IS NULL
                """, Integer.class, LATER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_delivery WHERE kind = 'ALERT'", Integer.class)).isZero();
    }

    @Test
    void a_pickup_without_meal_waits_for_the_recap() {
        var cook = volunteer();
        var courier = volunteer();
        var meal = registrations.registerByCoordinator(cook,
                RegistrationChoice.onEvening(SOON, RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH));
        registrations.registerByCoordinator(courier,
                RegistrationChoice.onEvening(SOON, RegistrationType.PICK_UP_AND_SHARE, null));

        registrations.cancelByCoordinator(meal.registrationId());
        jobs.tick();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id
                WHERE e.evening_date = ? AND a.kind = 'PICKUP_WITHOUT_MEAL'
                """, Integer.class, SOON)).isEqualTo(1);
        assertThat(mails.sentTo(coordinator)).noneMatch(mail -> mail.text().contains("récupération"));
    }

    @Test
    void an_alert_already_handled_is_not_mailed() throws Exception {
        breakfastCancelled(SOON);
        jdbc.update("UPDATE coordinator_alert SET handled_at = now(), handled_by = ? WHERE sent_at IS NULL",
                UUID.randomUUID());

        jobs.tick();

        assertThat(mails.sentTo(coordinator)).isEmpty();
    }

    @Test
    void without_coordinators_the_alert_is_not_mailed() throws Exception {
        var others = coordinators.all();
        coordinators.clear();
        try {
            breakfastCancelled(SOON);
            jobs.tick();

            assertThat(mails.sent()).noneMatch(mail -> mail.subject().contains("à compléter"));
            assertThat(jdbc.queryForObject("SELECT abandon_reason FROM mail_delivery WHERE kind = 'ALERT'", String.class))
                    .isEqualTo("NO_RECIPIENT");
        } finally {
            others.forEach(coordinators::add);
        }
    }

    /** Bénévole sans adresse au profil complet, inscrit par un coordinateur. */
    private UUID volunteer() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, profile_completed_at)
                VALUES (?, NULL, 'Durand', 'Jeanne', '+33600000003', ?, now())
                """, id, "J" + id.toString().substring(0, 8));
        return id;
    }

    private static void cancel(Browser browser, LocalDate eveningDate, String choice) throws Exception {
        browser.get("/mes-inscriptions/annuler?soiree=" + eveningDate + "&choix=" + choice);
        assertThat(browser.post("/mes-inscriptions/annuler", "soiree", eveningDate.toString(), "choix", choice)
                .getRedirectedUrl()).isEqualTo("/mes-inscriptions");
    }
}
