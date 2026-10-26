package com.marcantoinemeunier.hiversolidaire.inscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
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
 * Parcours « Se désinscrire » (cahier des charges §6, R6) : depuis « Mes inscriptions », page de confirmation puis
 * POST, jusqu'à la veille à 23 h 59 ; ensuite, le téléphone des coordinateurs. Un bénévole n'annule que ses propres
 * inscriptions (M9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class CancellationJourneyTest {

    /**
     * Soirée du vendredi 8 janvier 2027 : dans les 7 jours qui suivent l'horloge de test (4 janvier). Son
     * petit-déjeuner a lieu le samedi 9 au matin ; la limite reste la veille de la soirée, jeudi 7 à 23 h 59 (R6).
     */
    private static final LocalDate SOON = LocalDate.of(2027, 1, 8);
    private static final String SOON_LABEL = "Petit-déjeuner du sam. 9 janv.";
    private static final String SOON_DEADLINE = "Possible jusqu’au jeu. 7 janv. à 23 h 59";
    /** Mercredi 17 février 2027 : au-delà de la fenêtre de l'alerte. */
    private static final LocalDate LATER = LocalDate.of(2027, 2, 17);
    private static final String COORDINATORS_PHONE = "01 99 00 12 34";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @AfterEach
    void clean() {
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        for (var date : new LocalDate[] {SOON, LATER}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    record Volunteer(Browser browser, String email, String pseudo) {
    }

    private Volunteer signedIn() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        var pseudo = Browser.uniquePseudo();
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        browser.get("/profil");
        browser.post("/profil", "pseudo", pseudo, "lastName", "Martin", "firstName", "Anne", "phone", "0600000001");
        return new Volunteer(browser, email, pseudo);
    }

    private static void register(Browser browser, LocalDate date, String choice) throws Exception {
        // Inscription depuis la journée de l'activité : le lendemain de la soirée pour un petit-déjeuner.
        var day = "BREAKFAST".equals(choice) ? date.plusDays(1) : date;
        browser.get("/inscription/confirmer?jour=" + day + "&choix=" + choice);
        assertThat(browser.post("/inscription", "jour", day.toString(), "choix", choice).getRedirectedUrl())
                .isEqualTo("/inscription/confirmee");
    }

    private int active(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.cancelled_at IS NULL
                """, Integer.class, date);
    }

    private int cancelled(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.cancelled_at IS NOT NULL
                """, Integer.class, date);
    }

    private int alerts(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id
                WHERE e.evening_date = ? AND a.kind = 'EVENING_INCOMPLETE' AND a.sent_at IS NULL
                """, Integer.class, date);
    }

    @Test
    void a_volunteer_cancels_from_my_registrations(CapturedOutput output) throws Exception {
        var alice = signedIn();
        var browser = alice.browser();
        register(browser, SOON, "BREAKFAST");
        var visitor = new Browser(mockMvc);
        assertThat(visitor.get("/planning").getContentAsString()).contains(alice.pseudo());
        assertThat(browser.get("/planning").getContentAsString()).contains("Vous fournissez le petit-déjeuner");

        // 1. « Mes inscriptions » propose de se désinscrire.
        var mine = browser.get("/mes-inscriptions").getContentAsString();
        assertThat(mine).contains(SOON_LABEL).contains("href=\"/mes-inscriptions/annuler?soiree=" + SOON + "\"")
                .contains(SOON_DEADLINE);

        // 2. Le lien ouvre une page de confirmation : un GET n'annule rien.
        var confirm = browser.get("/mes-inscriptions/annuler?soiree=" + SOON);
        assertThat(confirm.getStatus()).isEqualTo(200);
        assertThat(confirm.getContentAsString()).contains(SOON_LABEL).contains(SOON_DEADLINE)
                .contains("action=\"/mes-inscriptions/annuler\"").contains("name=\"soiree\" value=\"" + SOON + "\"");
        assertThat(active(SOON)).isEqualTo(1);

        // 3. Le POST annule : l'inscription reste en base, marquée annulée.
        var post = browser.post("/mes-inscriptions/annuler", "soiree", SOON.toString());
        assertThat(post.getRedirectedUrl()).isEqualTo("/mes-inscriptions");
        assertThat(active(SOON)).isZero();
        assertThat(cancelled(SOON)).isEqualTo(1);

        // 4. Confirmation à l'écran, la soirée disparaît de « Mes inscriptions » et le pseudo du planning.
        var after = browser.get("/mes-inscriptions").getContentAsString();
        assertThat(after).contains("Désinscription enregistrée").contains("Aucune inscription à venir");
        assertThat(visitor.get("/planning").getContentAsString()).doesNotContain(alice.pseudo());
        assertThat(browser.get("/planning").getContentAsString()).doesNotContain("Vous fournissez");

        // 5. La place libérée est immédiatement reprenable, par elle comme par un autre.
        register(browser, SOON, "BREAKFAST");

        assertThat(output.getAll()).doesNotContain(alice.email()).doesNotContain(alice.pseudo())
                .doesNotContain("Martin").doesNotContain("+33600000001");
    }

    @Test
    void the_freed_place_is_taken_by_another_volunteer() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "PREPARE_AT_HOME.MAIN_DISH");
        alice.get("/mes-inscriptions/annuler?soiree=" + SOON);
        alice.post("/mes-inscriptions/annuler", "soiree", SOON.toString());

        register(signedIn().browser(), SOON, "PREPARE_AND_SHARE.MAIN_DISH");

        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void cancelling_a_minimum_within_seven_days_records_an_alert_for_the_coordinators() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");

        alice.get("/mes-inscriptions/annuler?soiree=" + SOON);
        alice.post("/mes-inscriptions/annuler", "soiree", SOON.toString());

        assertThat(alerts(SOON)).isEqualTo(1);
    }

    @Test
    void cancelling_beyond_seven_days_records_no_alert() throws Exception {
        var alice = signedIn().browser();
        register(alice, LATER, "BREAKFAST");

        alice.get("/mes-inscriptions/annuler?soiree=" + LATER);
        alice.post("/mes-inscriptions/annuler", "soiree", LATER.toString());

        assertThat(active(LATER)).isZero();
        assertThat(alerts(LATER)).isZero();
    }

    @Test
    void after_the_deadline_the_button_disappears_and_the_coordinators_phone_is_shown() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        alice.get("/mes-inscriptions/annuler?soiree=" + SOON);

        // Le jour même à 0 h, heure de Paris : trop tard.
        clock.set(LocalDateTime.of(2027, 1, 8, 0, 0).atZone(ParisTime.ZONE).toInstant());

        var mine = alice.get("/mes-inscriptions").getContentAsString();
        assertThat(mine).contains(SOON_LABEL).doesNotContain("/mes-inscriptions/annuler").doesNotContain(SOON_DEADLINE)
                .contains(COORDINATORS_PHONE).contains("href=\"tel:0199001234\"");
        var page = alice.get("/mes-inscriptions/annuler?soiree=" + SOON).getContentAsString();
        assertThat(page).contains(COORDINATORS_PHONE).doesNotContain("action=\"/mes-inscriptions/annuler\"")
                .contains("la limite était le jeu. 7 janv. à 23 h 59");

        // Un POST forgé est refusé : l'inscription reste.
        var post = alice.post("/mes-inscriptions/annuler", "soiree", SOON.toString());
        assertThat(post.getRedirectedUrl()).isEqualTo("/mes-inscriptions/annuler?soiree=" + SOON);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void the_day_before_at_23_59_cancellation_is_still_possible() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        clock.set(LocalDateTime.of(2027, 1, 7, 23, 59).atZone(ParisTime.ZONE).toInstant());

        alice.get("/mes-inscriptions/annuler?soiree=" + SOON);
        alice.post("/mes-inscriptions/annuler", "soiree", SOON.toString());

        assertThat(active(SOON)).isZero();
    }

    @Test
    void a_volunteer_cannot_see_nor_cancel_the_registration_of_another() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        var bob = signedIn().browser();

        assertThat(bob.get("/mes-inscriptions/annuler?soiree=" + SOON).getStatus()).isEqualTo(404);
        bob.get("/mes-inscriptions");
        assertThat(bob.post("/mes-inscriptions/annuler", "soiree", SOON.toString()).getStatus()).isEqualTo(404);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void cancellation_without_csrf_token_is_refused() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");

        assertThat(alice.postWithoutCsrf("/mes-inscriptions/annuler", "soiree", SOON.toString()).getStatus())
                .isEqualTo(403);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void anonymous_visitors_cannot_cancel() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        var anonymous = new Browser(mockMvc);

        var page = anonymous.get("/mes-inscriptions/annuler?soiree=" + SOON);
        assertThat(page.getRedirectedUrl()).endsWith("/connexion");
        anonymous.get("/connexion");
        assertThat(anonymous.post("/mes-inscriptions/annuler", "soiree", SOON.toString()).getStatus()).isEqualTo(403);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void an_unknown_or_malformed_evening_is_not_found() throws Exception {
        var alice = signedIn().browser();

        assertThat(alice.get("/mes-inscriptions/annuler?soiree=2027-13-01").getStatus()).isEqualTo(404);
        assertThat(alice.get("/mes-inscriptions/annuler?soiree=" + SOON).getStatus()).isEqualTo(404);
    }
}
