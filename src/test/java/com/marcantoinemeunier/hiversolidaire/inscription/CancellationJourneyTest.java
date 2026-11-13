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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Parcours « Se désinscrire » (cahier des charges §6, R6) : depuis « Mes inscriptions », page de confirmation puis
 * POST, jusqu'à la veille à 23 h 59 ; ensuite, le téléphone des coordinateurs. Un bénévole n'annule que ses propres
 * inscriptions (M9). L'inscription est désignée par sa soirée et son choix ({@code soiree}, {@code choix}) : un
 * bénévole peut tenir plusieurs postes d'une soirée (R2, ADR-0011).
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
    private static final String CONTACT_SENTENCE = "Pour vous désinscrire, contactez les coordinateurs via";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    /** Le contact ne vient que des réglages de la saison : celle d'exemple le reçoit le temps du test. */
    @BeforeEach
    void seasonContact() {
        jdbc.update("UPDATE season SET coordinators_contact = ?", "01 99 00 12 34");
    }

    @AfterEach
    void clean() {
        jdbc.update("UPDATE season SET coordinators_contact = NULL");
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
        browser.requestLink(email);
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
        return alerts(date, "EVENING_INCOMPLETE");
    }

    private int alerts(LocalDate date, String kind) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id
                WHERE e.evening_date = ? AND a.kind = ? AND a.sent_at IS NULL
                """, Integer.class, date, kind);
    }

    private static String cancelUrl(LocalDate date, String choice) {
        return "/mes-inscriptions/annuler?soiree=" + date + "&choix=" + choice;
    }

    /** Page de confirmation, puis POST : ce que fait le bouton « Me désinscrire ». */
    private static MockHttpServletResponse cancel(Browser browser, LocalDate date, String choice) throws Exception {
        browser.get(cancelUrl(date, choice));
        return browser.post("/mes-inscriptions/annuler", "soiree", date.toString(), "choix", choice);
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
        assertThat(mine).contains(SOON_LABEL)
                .contains("href=\"/mes-inscriptions/annuler?soiree=" + SOON + "&amp;choix=BREAKFAST\"")
                .contains(SOON_DEADLINE);

        // 2. Le lien ouvre une page de confirmation : un GET n'annule rien.
        var confirm = browser.get(cancelUrl(SOON, "BREAKFAST"));
        assertThat(confirm.getStatus()).isEqualTo(200);
        assertThat(confirm.getContentAsString()).contains(SOON_LABEL).contains(SOON_DEADLINE)
                .contains("action=\"/mes-inscriptions/annuler\"").contains("name=\"soiree\" value=\"" + SOON + "\"")
                .contains("name=\"choix\" value=\"BREAKFAST\"")
                .doesNotContain("récupérer ce repas");
        assertThat(active(SOON)).isEqualTo(1);

        // 3. Le POST annule : l'inscription reste en base, marquée annulée.
        var post = browser.post("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix", "BREAKFAST");
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
        cancel(alice, SOON, "PREPARE_AT_HOME.MAIN_DISH");

        register(signedIn().browser(), SOON, "PREPARE_AND_SHARE.MAIN_DISH");

        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void cancelling_a_minimum_within_seven_days_records_an_alert_for_the_coordinators() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");

        cancel(alice, SOON, "BREAKFAST");

        assertThat(alerts(SOON)).isEqualTo(1);
    }

    @Test
    void cancelling_beyond_seven_days_records_no_alert() throws Exception {
        var alice = signedIn().browser();
        register(alice, LATER, "BREAKFAST");

        cancel(alice, LATER, "BREAKFAST");

        assertThat(active(LATER)).isZero();
        assertThat(alerts(LATER)).isZero();
    }

    @Test
    void after_the_deadline_the_button_disappears_and_the_coordinators_contact_is_shown() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        alice.get(cancelUrl(SOON, "BREAKFAST"));

        // Le jour même à 0 h, heure de Paris : trop tard.
        clock.set(LocalDateTime.of(2027, 1, 8, 0, 0).atZone(ParisTime.ZONE).toInstant());

        var mine = alice.get("/mes-inscriptions").getContentAsString();
        assertThat(mine).contains(SOON_LABEL).doesNotContain("/mes-inscriptions/annuler").doesNotContain(SOON_DEADLINE)
                .contains(CONTACT_SENTENCE).contains(COORDINATORS_PHONE).contains("href=\"tel:0199001234\"");
        var page = alice.get(cancelUrl(SOON, "BREAKFAST")).getContentAsString();
        assertThat(page).contains(CONTACT_SENTENCE).contains(COORDINATORS_PHONE).doesNotContain("action=\"/mes-inscriptions/annuler\"")
                .contains("la limite était le jeu. 7 janv. à 23 h 59");

        // Un POST forgé est refusé : l'inscription reste.
        var post = alice.post("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix", "BREAKFAST");
        assertThat(post.getRedirectedUrl()).isEqualTo(cancelUrl(SOON, "BREAKFAST"));
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void the_day_before_at_23_59_cancellation_is_still_possible() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        clock.set(LocalDateTime.of(2027, 1, 7, 23, 59).atZone(ParisTime.ZONE).toInstant());

        cancel(alice, SOON, "BREAKFAST");

        assertThat(active(SOON)).isZero();
    }

    @Test
    void a_volunteer_cannot_see_nor_cancel_the_registration_of_another() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        var bob = signedIn().browser();

        assertThat(bob.get(cancelUrl(SOON, "BREAKFAST")).getStatus()).isEqualTo(404);
        bob.get("/mes-inscriptions");
        assertThat(bob.post("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix", "BREAKFAST").getStatus())
                .isEqualTo(404);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void cancellation_without_csrf_token_is_refused() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");

        assertThat(alice.postWithoutCsrf("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix", "BREAKFAST")
                .getStatus()).isEqualTo(403);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void anonymous_visitors_cannot_cancel() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");
        var anonymous = new Browser(mockMvc);

        var page = anonymous.get(cancelUrl(SOON, "BREAKFAST"));
        assertThat(page.getRedirectedUrl()).endsWith("/connexion");
        anonymous.get("/connexion");
        assertThat(anonymous.post("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix", "BREAKFAST")
                .getStatus()).isEqualTo(403);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void an_unknown_or_malformed_evening_is_not_found() throws Exception {
        var alice = signedIn().browser();

        assertThat(alice.get(cancelUrl(LocalDate.of(2027, 1, 1), "BREAKFAST").replace("2027-01-01", "2027-13-01"))
                .getStatus()).isEqualTo(404);
        assertThat(alice.get(cancelUrl(SOON, "BREAKFAST")).getStatus()).isEqualTo(404);
    }

    @Test
    void a_missing_unknown_or_unheld_choice_is_not_found() throws Exception {
        var alice = signedIn().browser();
        register(alice, SOON, "BREAKFAST");

        assertThat(alice.get("/mes-inscriptions/annuler?soiree=" + SOON).getStatus()).isEqualTo(404);
        assertThat(alice.get(cancelUrl(SOON, "INCONNU")).getStatus()).isEqualTo(404);
        assertThat(alice.get(cancelUrl(SOON, "BREAKFAST.MAIN_DISH")).getStatus()).isEqualTo(404);
        assertThat(alice.get(cancelUrl(SOON, "NIGHT_AND_DINNER")).getStatus()).isEqualTo(404);
        alice.get("/mes-inscriptions");
        assertThat(alice.post("/mes-inscriptions/annuler", "soiree", SOON.toString()).getStatus()).isEqualTo(404);
        assertThat(alice.post("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix", "NIGHT_AND_DINNER")
                .getStatus()).isEqualTo(404);
        assertThat(active(SOON)).isEqualTo(1);
    }

    @Test
    void a_volunteer_cancels_one_post_and_keeps_the_others_of_the_same_evening() throws Exception {
        var alice = signedIn().browser();
        register(alice, LATER, "NIGHT_AND_DINNER");
        register(alice, LATER, "PREPARE_AND_SHARE.MAIN_DISH");
        register(alice, LATER, "BREAKFAST");
        var mine = alice.get("/mes-inscriptions").getContentAsString();
        assertThat(mine).contains("soiree=" + LATER + "&amp;choix=NIGHT_AND_DINNER")
                .contains("soiree=" + LATER + "&amp;choix=PREPARE_AND_SHARE.MAIN_DISH")
                .contains("soiree=" + LATER + "&amp;choix=BREAKFAST");

        var post = cancel(alice, LATER, "PREPARE_AND_SHARE.MAIN_DISH");

        assertThat(post.getRedirectedUrl()).isEqualTo("/mes-inscriptions");
        assertThat(active(LATER)).isEqualTo(2);
        var after = alice.get("/mes-inscriptions").getContentAsString();
        assertThat(after).contains("Désinscription enregistrée").contains("choix=NIGHT_AND_DINNER")
                .contains("choix=BREAKFAST").doesNotContain("choix=PREPARE_AND_SHARE.MAIN_DISH");
        assertThat(jdbc.queryForList("""
                SELECT r.type FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.cancelled_at IS NOT NULL
                """, String.class, LATER)).containsExactly("PREPARE_AND_SHARE");
    }

    @Test
    void cancelling_a_home_preparation_already_picked_up_warns_and_alerts_the_coordinators() throws Exception {
        var alice = signedIn().browser();
        register(alice, LATER, "PREPARE_AT_HOME.MAIN_DISH");
        var bob = signedIn().browser();
        register(bob, LATER, "PICK_UP_AND_SHARE");

        var confirm = alice.get(cancelUrl(LATER, "PREPARE_AT_HOME.MAIN_DISH")).getContentAsString();
        assertThat(confirm).contains("Une personne doit venir récupérer ce repas");
        var post = alice.post("/mes-inscriptions/annuler", "soiree", LATER.toString(), "choix",
                "PREPARE_AT_HOME.MAIN_DISH");

        // Acceptée (R6 inchangée) ; la récupération reste et les coordinateurs sont alertés, même au-delà de 7 jours.
        assertThat(post.getRedirectedUrl()).isEqualTo("/mes-inscriptions");
        assertThat(active(LATER)).isEqualTo(1);
        assertThat(alerts(LATER, "PICKUP_WITHOUT_MEAL")).isEqualTo(1);
        assertThat(alerts(LATER)).isZero();
    }

    @Test
    void cancelling_a_home_preparation_nobody_picks_up_neither_warns_nor_alerts() throws Exception {
        var alice = signedIn().browser();
        register(alice, LATER, "PREPARE_AT_HOME.MAIN_DISH");

        assertThat(alice.get(cancelUrl(LATER, "PREPARE_AT_HOME.MAIN_DISH")).getContentAsString())
                .doesNotContain("Une personne doit venir récupérer ce repas");
        cancel(alice, LATER, "PREPARE_AT_HOME.MAIN_DISH");

        assertThat(active(LATER)).isZero();
        assertThat(alerts(LATER, "PICKUP_WITHOUT_MEAL")).isZero();
    }
}
