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
 * Récap quotidien des coordinateurs à 20 h (cahier des charges §6, plan B du §8) : le lundi 4 janvier 2027 à 20 h, les
 * journées du mardi 5 au lundi 11, par journée ce qui manque et qui vient (nom, pseudo, téléphone, jamais l'adresse
 * e-mail), les alertes non traitées et les nouveaux bénévoles. Un seul par jour ; aucun sans coordinateur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class DailyRecapMailTest {

    /** Soirée du mardi 5 : son petit-déjeuner est sur la carte du mercredi 6. */
    private static final LocalDate TUESDAY = LocalDate.of(2027, 1, 5);
    private static final LocalDate WEDNESDAY = LocalDate.of(2027, 1, 6);
    private static final LocalDate THURSDAY = LocalDate.of(2027, 1, 7);
    /** Vendredi 8 : à plus de 48 h, son alerte n'est que dans le récap. */
    private static final LocalDate FRIDAY = LocalDate.of(2027, 1, 8);
    private static final String SUBJECT = "Récap du planning du mar. 5 janv. au lun. 11 janv. — Hiver Solidaire";

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
        for (var date : new LocalDate[] {TUESDAY, WEDNESDAY, THURSDAY, FRIDAY}) {
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

    private List<String> recaps() {
        return mails.sentTo(coordinator).stream().filter(mail -> mail.subject().startsWith("Récap"))
                .map(mail -> mail.subject()).toList();
    }

    @Test
    void the_recap_gives_each_day_its_gaps_and_its_people(CapturedOutput output) throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "BREAKFAST");
        TestVolunteers.register(anne.browser(), WEDNESDAY, "NIGHT_AND_DINNER");
        var phoneOnly = withoutEmail();
        registrations.registerByCoordinator(phoneOnly,
                RegistrationChoice.onEvening(THURSDAY, RegistrationType.NIGHT_AND_DINNER, null));
        // Désinscription au-delà de 48 h : l'alerte attend le récap.
        TestVolunteers.register(anne.browser(), FRIDAY, "BREAKFAST");
        cancel(anne.browser(), FRIDAY, "BREAKFAST");

        at(4, 19, 59);
        assertThat(recaps()).isEmpty();
        at(4, 20, 0);

        assertThat(mails.sentTo(coordinator).stream().filter(mail -> mail.subject().equals(SUBJECT)))
                .singleElement().satisfies(mail -> {
                    assertThat(mail.to()).contains(coordinator);
                    assertThat(mail.replyTo()).isNull();
                    assertThat(mail.text())
                            .containsSubsequence("Bonjour,",
                                    "Vous recevez ce mail car vous êtes administrateur Hiver Solidaire.")
                            .contains("Voici le planning des journées du mar. 5 janv. au lun. 11 janv. :")
                            // Mercredi 6 : le petit-déjeuner (soirée du mardi) puis la nuit du mercredi.
                            .containsSubsequence("== Mer. 6 janv. : à compléter ==",
                                    "! Plat principal : 1 à pourvoir",
                                    "- Fournir et partager le petit-déjeuner : Anne Martin (" + anne.pseudo()
                                            + "), +33600000001",
                                    "- Passer la nuit et partager le dîner : Anne Martin (" + anne.pseudo() + ")",
                                    "== Jeu. 7 janv.")
                            .containsSubsequence("== Jeu. 7 janv. : à compléter ==",
                                    "- Passer la nuit et partager le dîner : Jeanne Durand",
                                    "(sans e-mail : à prévenir par téléphone)")
                            .containsSubsequence("== Alertes non traitées ==",
                                    "- Soirée du ven. 8 janv. devenue incomplète après une désinscription")
                            .containsSubsequence("== Nouveaux bénévoles à signaler ==", anne.pseudo())
                            .contains("Back-office : http://localhost:8080/admin")
                            .contains("Ce récap contient des coordonnées : ne le transférez pas");
                    // Plan B : aucune adresse de bénévole, ni dans le texte, ni dans le HTML.
                    assertThat(mail.text()).doesNotContain(anne.email());
                    assertThat(mail.html()).doesNotContain(anne.email())
                            .contains("<h2><span>Mer. 6 janv.</span> : <span>à compléter</span></h2>");
                });
        assertThat(output).doesNotContain(anne.email()).doesNotContain(coordinator);
    }

    @Test
    void one_recap_a_day_even_after_a_restart() {
        at(4, 20, 0);
        at(4, 23, 0);
        at(5, 7, 59);
        assertThat(recaps()).hasSize(1);

        at(5, 8, 0);
        at(5, 20, 0);
        assertThat(recaps()).hasSize(2);
        assertThat(recaps().getLast()).isEqualTo("Récap du planning du mer. 6 janv. au mar. 12 janv. — Hiver Solidaire");
    }

    @Test
    void a_missed_recap_is_caught_up_until_eight_am() {
        // L'appli était arrêtée de 19 h à 7 h le lendemain.
        at(5, 7, 0);

        assertThat(recaps()).containsExactly(SUBJECT);
    }

    @Test
    void no_recap_without_coordinators() {
        var others = coordinators.all();
        coordinators.clear();
        try {
            at(4, 20, 0);

            assertThat(mails.sent()).noneMatch(mail -> mail.subject().startsWith("Récap"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_delivery WHERE kind = 'DAILY_RECAP'",
                    Integer.class)).isZero();
        } finally {
            others.forEach(coordinators::add);
        }
    }

    @Test
    void no_recap_after_the_season() {
        // Saison d'exemple : dernière soirée le dimanche 21 mars 2027.
        clock.set(LocalDateTime.of(2027, 3, 22, 20, 0).atZone(ParisTime.ZONE).toInstant());
        jobs.tick();

        assertThat(recaps()).isEmpty();
    }

    private UUID withoutEmail() {
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
