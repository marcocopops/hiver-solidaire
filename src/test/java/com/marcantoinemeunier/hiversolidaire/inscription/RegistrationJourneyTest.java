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
import java.util.regex.Pattern;
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
 * Parcours « S'inscrire » de bout en bout (cahier des charges §6) : planning, choix, demande de lien, connexion
 * sur un autre appareil, premier profil, confirmation, « Mes inscriptions », statut mis à jour sur le planning. On
 * s'inscrit depuis une journée (ADR-0009) : le petit-déjeuner sur la soirée de la veille, le reste sur celle du jour.
 * L'envoi est remplacé par un double de test ; aucun e-mail ni jeton ne doit apparaître dans les logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class RegistrationJourneyTest {

    /** Mercredi 6 janvier 2027, journée de la saison d'exemple (horloge de test au 4 janvier). */
    private static final LocalDate DAY = LocalDate.of(2027, 1, 6);
    private static final String LABEL = "Mer. 6 janv.";
    private static final String NIGHT_LABEL = "Dîner et nuit du mer. 6 janv.";

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
        for (var date : new LocalDate[] {DAY.minusDays(1), DAY}) {
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    /** Classe CSS de la carte de cette journée sur le planning : son statut (R5). */
    private static String statusOnPlanning(String planning) {
        var matcher = Pattern.compile("journee--(\\w+)\"[^>]*>\\s*<summary[^>]*>\\s*<span class=\"journee__date\">"
                + Pattern.quote(LABEL) + "<").matcher(planning);
        assertThat(matcher.find()).as("journée affichée sur le planning").isTrue();
        return matcher.group(1);
    }

    /** Soirée où l'inscription active de ce type a été enregistrée. */
    private LocalDate eveningOf(String type) {
        return jdbc.queryForObject("""
                SELECT e.evening_date FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.type = ? AND r.cancelled_at IS NULL AND e.evening_date IN (?, ?)
                """, LocalDate.class, type, DAY.minusDays(1), DAY);
    }

    @Test
    void a_new_volunteer_registers_from_the_planning(CapturedOutput output) throws Exception {
        var computer = new Browser(mockMvc);
        var email = Browser.uniqueEmail();

        // 1. Le planning propose de s'inscrire sur la journée, encore vide.
        var planning = computer.get("/planning").getContentAsString();
        assertThat(statusOnPlanning(planning)).isEqualTo("incomplete");
        assertThat(planning).contains("href=\"/inscription?jour=" + DAY + "\"");

        // 2. Choix d'un type encore disponible.
        var choicePage = computer.get("/inscription?jour=" + DAY).getContentAsString();
        assertThat(choicePage).contains(LABEL).contains("value=\"NIGHT_AND_DINNER\"")
                .contains("action=\"/connexion\"");

        // 3. Demande du lien, le choix est transmis.
        var loginPage = computer.get("/connexion?jour=" + DAY + "&choix=NIGHT_AND_DINNER").getContentAsString();
        assertThat(loginPage).contains("name=\"jour\" value=\"" + DAY + "\"")
                .contains("name=\"choix\" value=\"NIGHT_AND_DINNER\"");
        assertThat(computer.requestLink(email, "jour", DAY.toString(),
                "choix", "NIGHT_AND_DINNER").getRedirectedUrl()).isEqualTo("/connexion/envoye");
        var link = sender.lastSentTo(email).orElseThrow();
        assertThat(link.link().getQuery()).contains("jour=" + DAY).contains("choix=NIGHT_AND_DINNER");

        // 4. Le lien est ouvert sur le téléphone : le choix le suit.
        var phone = computer.otherDevice();
        var confirmLogin = phone.get(link.link().getRawPath() + "?" + link.link().getRawQuery()).getContentAsString();
        assertThat(confirmLogin).contains("name=\"jour\" value=\"" + DAY + "\"");
        var afterLogin = phone.post("/login/ott", "token", link.token(), "jour", DAY.toString(),
                "choix", "NIGHT_AND_DINNER");
        assertThat(afterLogin.getRedirectedUrl()).isEqualTo("/profil?jour=" + DAY + "&choix=NIGHT_AND_DINNER");

        // 5. Premier profil.
        phone.get(afterLogin.getRedirectedUrl());
        var afterProfile = phone.post("/profil", "pseudo", Browser.uniquePseudo(), "lastName", "Martin", "firstName", "Anne",
                "phone", "06 00 00 00 01", "jour", DAY.toString(), "choix", "NIGHT_AND_DINNER");
        assertThat(afterProfile.getRedirectedUrl())
                .isEqualTo("/inscription/confirmer?jour=" + DAY + "&choix=NIGHT_AND_DINNER");

        // 6. Confirmation par POST.
        var confirmPage = phone.get(afterProfile.getRedirectedUrl()).getContentAsString();
        assertThat(confirmPage).contains(NIGHT_LABEL).contains("Passer la nuit");
        var afterConfirm = phone.post("/inscription", "jour", DAY.toString(), "choix", "NIGHT_AND_DINNER");
        assertThat(afterConfirm.getRedirectedUrl()).isEqualTo("/inscription/confirmee");
        assertThat(phone.get("/inscription/confirmee").getContentAsString())
                .contains("Inscription confirmée").contains(NIGHT_LABEL);

        // 7. « Mes inscriptions » et le planning reflètent l'inscription.
        assertThat(phone.get("/mes-inscriptions").getContentAsString()).contains(NIGHT_LABEL)
                .contains("Passer la nuit");
        assertThat(eveningOf("NIGHT_AND_DINNER")).isEqualTo(DAY);
        assertThat(jdbc.queryForObject("SELECT phone FROM volunteer WHERE email = ?", String.class, email))
                .isEqualTo("+33600000001");
        var updated = phone.get("/planning").getContentAsString();
        assertThat(statusOnPlanning(updated)).isEqualTo("incomplete");
        assertThat(updated).contains("Vous passez la nuit");
        assertThat(statusOnPlanning(new Browser(mockMvc).get("/planning").getContentAsString()))
                .isEqualTo("incomplete");

        // 8. Aucune donnée personnelle ni jeton dans les logs (CLAUDE.md, menace M13).
        assertThat(output.getAll()).doesNotContain(email).doesNotContain("@example.org")
                .doesNotContain(link.token()).doesNotContain("Martin").doesNotContain("600000001");
    }

    @Test
    void an_existing_volunteer_goes_straight_to_the_confirmation(CapturedOutput output) throws Exception {
        var email = Browser.uniqueEmail();
        var first = new Browser(mockMvc);
        first.get("/connexion");
        first.requestLink(email);
        var firstToken = sender.lastSentTo(email).orElseThrow().token();
        first.get("/login/ott?token=" + firstToken);
        first.post("/login/ott", "token", firstToken);
        first.get("/profil");
        first.post("/profil", "pseudo", Browser.uniquePseudo(), "lastName", "Martin", "firstName", "Anne", "phone", "0600000001");

        var browser = new Browser(mockMvc);
        browser.get("/connexion?jour=" + DAY + "&choix=BREAKFAST");
        browser.requestLink(email, "jour", DAY.toString(), "choix", "BREAKFAST");
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);

        var afterLogin = browser.post("/login/ott", "token", token, "jour", DAY.toString(), "choix", "BREAKFAST");

        assertThat(afterLogin.getRedirectedUrl())
                .isEqualTo("/inscription/confirmer?jour=" + DAY + "&choix=BREAKFAST");
        assertThat(output.getAll()).doesNotContain(email).doesNotContain(token);
    }

    @Test
    void a_signed_in_volunteer_chooses_and_confirms_without_a_new_link() throws Exception {
        var browser = signedInWithProfile();

        var choicePage = browser.get("/inscription?jour=" + DAY).getContentAsString();

        assertThat(choicePage).contains("action=\"/inscription/confirmer\"");
        browser.get("/inscription/confirmer?jour=" + DAY + "&choix=PREPARE_AT_HOME.SIDES");
        assertThat(browser.post("/inscription", "jour", DAY.toString(), "choix", "PREPARE_AT_HOME.SIDES")
                .getRedirectedUrl()).isEqualTo("/inscription/confirmee");
    }

    @Test
    void a_full_type_is_no_longer_offered_and_its_confirmation_is_refused() throws Exception {
        var first = signedInWithProfile();
        first.get("/inscription/confirmer?jour=" + DAY + "&choix=PREPARE_AT_HOME.MAIN_DISH");
        first.post("/inscription", "jour", DAY.toString(), "choix", "PREPARE_AT_HOME.MAIN_DISH");

        var second = signedInWithProfile();
        var choicePage = second.get("/inscription?jour=" + DAY).getContentAsString();
        assertThat(choicePage).doesNotContain("value=\"PREPARE_AT_HOME.MAIN_DISH\"")
                .contains("value=\"PREPARE_AT_HOME.SIDES\"");

        second.get("/inscription/confirmer?jour=" + DAY + "&choix=PREPARE_AND_SHARE.MAIN_DISH");
        var refused = second.post("/inscription", "jour", DAY.toString(), "choix", "PREPARE_AND_SHARE.MAIN_DISH");

        assertThat(refused.getRedirectedUrl()).isEqualTo("/inscription?jour=" + DAY + "&refus=DISH_TAKEN");
        assertThat(second.get(refused.getRedirectedUrl()).getContentAsString())
                .contains("Ce plat est déjà préparé par quelqu’un d’autre");
    }

    @Test
    void breakfast_is_registered_on_the_previous_evening_and_dinner_on_the_same_evening() throws Exception {
        var browser = signedInWithProfile();
        browser.get("/inscription/confirmer?jour=" + DAY + "&choix=BREAKFAST");
        assertThat(browser.post("/inscription", "jour", DAY.toString(), "choix", "BREAKFAST").getRedirectedUrl())
                .isEqualTo("/inscription/confirmee");
        assertThat(browser.get("/inscription/confirmee").getContentAsString())
                .contains("Petit-déjeuner du mer. 6 janv.");

        // Le dîner du même jour est sur une autre soirée : il reste possible.
        browser.get("/inscription/confirmer?jour=" + DAY + "&choix=NIGHT_AND_DINNER");
        assertThat(browser.post("/inscription", "jour", DAY.toString(), "choix", "NIGHT_AND_DINNER")
                .getRedirectedUrl()).isEqualTo("/inscription/confirmee");

        assertThat(eveningOf("BREAKFAST")).isEqualTo(DAY.minusDays(1));
        assertThat(eveningOf("NIGHT_AND_DINNER")).isEqualTo(DAY);
        assertThat(browser.get("/mes-inscriptions").getContentAsString())
                .containsSubsequence("Petit-déjeuner du mer. 6 janv.", "Dîner et nuit du mer. 6 janv.");
    }

    /** Inscrit le bénévole connecté par le parcours : page de confirmation, puis POST. */
    private static String register(Browser browser, String choice) throws Exception {
        browser.get("/inscription/confirmer?jour=" + DAY + "&choix=" + choice);
        return browser.post("/inscription", "jour", DAY.toString(), "choix", choice).getRedirectedUrl();
    }

    private int active(LocalDate evening) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE e.evening_date = ? AND r.cancelled_at IS NULL
                """, Integer.class, evening);
    }

    @Test
    void a_volunteer_cumulates_the_night_the_main_dish_and_the_sides_of_an_evening() throws Exception {
        var browser = signedInWithProfile();

        assertThat(register(browser, "NIGHT_AND_DINNER")).isEqualTo("/inscription/confirmee");
        var choicePage = browser.get("/inscription?jour=" + DAY).getContentAsString();
        assertThat(choicePage).contains("value=\"PREPARE_AND_SHARE.MAIN_DISH\"").contains("value=\"BREAKFAST\"")
                .doesNotContain("value=\"NIGHT_AND_DINNER\"");
        assertThat(register(browser, "PREPARE_AND_SHARE.MAIN_DISH")).isEqualTo("/inscription/confirmee");
        assertThat(register(browser, "PREPARE_AND_SHARE.SIDES")).isEqualTo("/inscription/confirmee");

        assertThat(active(DAY)).isEqualTo(3);
        assertThat(browser.get("/mes-inscriptions").getContentAsString())
                .contains("Préparer le plat principal (repas pour 8) et partager le dîner")
                .contains("Préparer les accompagnements (soupe, pain, fromage, dessert) et partager le dîner")
                .contains("Passer la nuit et partager le dîner");
    }

    @Test
    void a_volunteer_cannot_take_the_same_post_twice_on_the_same_evening() throws Exception {
        var browser = signedInWithProfile();
        register(browser, "NIGHT_AND_DINNER");

        var refused = register(browser, "NIGHT_AND_DINNER");

        assertThat(refused).isEqualTo("/inscription?jour=" + DAY + "&refus=ALREADY_REGISTERED");
        var choicePage = browser.get(refused).getContentAsString();
        assertThat(choicePage).contains("Vous avez déjà ce poste sur cette soirée")
                .contains("value=\"BREAKFAST\"").contains("value=\"PREPARE_AT_HOME.MAIN_DISH\"")
                .doesNotContain("value=\"NIGHT_AND_DINNER\"");
        assertThat(active(DAY)).isEqualTo(1);
    }

    @Test
    void the_pickup_is_offered_only_while_a_meal_prepared_at_home_waits_for_it() throws Exception {
        var visitor = new Browser(mockMvc);
        assertThat(visitor.get("/inscription?jour=" + DAY).getContentAsString())
                .doesNotContain("value=\"PICK_UP_AND_SHARE\"");

        var alice = signedInWithProfile();
        assertThat(register(alice, "PREPARE_AT_HOME.MAIN_DISH")).isEqualTo("/inscription/confirmee");
        assertThat(visitor.get("/inscription?jour=" + DAY).getContentAsString())
                .contains("value=\"PICK_UP_AND_SHARE\"");
        // Celle qui prépare chez elle ne récupère pas son propre repas.
        assertThat(alice.get("/inscription?jour=" + DAY).getContentAsString())
                .doesNotContain("value=\"PICK_UP_AND_SHARE\"");

        var bob = signedInWithProfile();
        assertThat(register(bob, "PICK_UP_AND_SHARE")).isEqualTo("/inscription/confirmee");
        assertThat(visitor.get("/inscription?jour=" + DAY).getContentAsString())
                .doesNotContain("value=\"PICK_UP_AND_SHARE\"");

        // Même contrôle en écriture : une confirmation arrivée trop tard est refusée.
        var carol = signedInWithProfile();
        var refused = register(carol, "PICK_UP_AND_SHARE");
        assertThat(refused).isEqualTo("/inscription?jour=" + DAY + "&refus=NOTHING_TO_PICK_UP");
        assertThat(carol.get(refused).getContentAsString())
                .contains("Il n’y a pas de repas préparé chez soi à récupérer");
        assertThat(active(DAY)).isEqualTo(2);
    }

    @Test
    void a_pickup_is_refused_beside_another_post_of_the_same_dinner() throws Exception {
        register(signedInWithProfile(), "PREPARE_AT_HOME.MAIN_DISH");
        var alice = signedInWithProfile();
        register(alice, "NIGHT_AND_DINNER");

        var refused = register(alice, "PICK_UP_AND_SHARE");

        assertThat(refused).isEqualTo("/inscription?jour=" + DAY + "&refus=NOT_CUMULABLE");
        assertThat(alice.get(refused).getContentAsString())
                .contains("ne se cumule qu’avec le petit-déjeuner");
        assertThat(active(DAY)).isEqualTo(2);
    }

    @Test
    void an_unknown_day_is_not_found() throws Exception {
        var browser = new Browser(mockMvc);

        assertThat(browser.get("/inscription?jour=2030-01-01").getStatus()).isEqualTo(404);
        assertThat(browser.get("/inscription?jour=2027-03-22").getStatus()).isEqualTo(404);
        assertThat(browser.get("/inscription?jour=pas-une-date").getStatus()).isEqualTo(404);
    }

    @Test
    void the_choice_of_a_day_lists_breakfast_first_then_the_evening() throws Exception {
        var page = new Browser(mockMvc).get("/inscription?jour=" + DAY).getContentAsString();

        assertThat(page).contains(LABEL).containsSubsequence("value=\"BREAKFAST\"",
                "value=\"PREPARE_AND_SHARE.MAIN_DISH\"", "value=\"NIGHT_AND_DINNER\"");
    }

    @Test
    void the_first_day_offers_no_breakfast_and_the_last_day_only_breakfast() throws Exception {
        var first = new Browser(mockMvc).get("/inscription?jour=2027-01-04").getContentAsString();
        var last = new Browser(mockMvc).get("/inscription?jour=2027-03-21").getContentAsString();

        assertThat(first).doesNotContain("value=\"BREAKFAST\"").contains("value=\"NIGHT_AND_DINNER\"");
        assertThat(last).contains("value=\"BREAKFAST\"").doesNotContain("value=\"NIGHT_AND_DINNER\"");
    }

    @Test
    void the_breakfast_of_today_is_no_longer_offered() throws Exception {
        // Mercredi 6 à 6 h : la soirée du 5 est passée, son petit-déjeuner aussi ; le dîner du 6 reste ouvert.
        clock.set(LocalDateTime.of(2027, 1, 6, 6, 0).atZone(ParisTime.ZONE).toInstant());

        var page = new Browser(mockMvc).get("/inscription?jour=" + DAY).getContentAsString();

        assertThat(page).doesNotContain("value=\"BREAKFAST\"").contains("value=\"NIGHT_AND_DINNER\"");
    }

    private Browser signedInWithProfile() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        browser.requestLink(email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        browser.get("/profil");
        browser.post("/profil", "pseudo", Browser.uniquePseudo(), "lastName", "Martin", "firstName", "Anne", "phone", "0600000001");
        return browser;
    }
}
