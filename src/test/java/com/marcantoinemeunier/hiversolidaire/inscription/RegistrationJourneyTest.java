package com.marcantoinemeunier.hiversolidaire.inscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.LocalDate;
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
 * sur un autre appareil, premier profil, confirmation, « Mes inscriptions », statut mis à jour sur le planning.
 * L'envoi est remplacé par un double de test ; aucun e-mail ni jeton ne doit apparaître dans les logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class RegistrationJourneyTest {

    /** Mercredi 6 → jeudi 7 janvier 2027, soirée de la saison d'exemple (horloge de test au 4 janvier). */
    private static final LocalDate EVENING = LocalDate.of(2027, 1, 6);
    private static final String LABEL = "Mer. 6 → jeu. 7 janv.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                EVENING);
    }

    /** Classe CSS de la ligne de cette soirée sur le planning : son statut (R5). */
    private static String statusOnPlanning(String planning) {
        var matcher = Pattern.compile("class=\"soiree soiree--(\\w+)\">\\s*<span class=\"soiree__date\">"
                + Pattern.quote(LABEL)).matcher(planning);
        assertThat(matcher.find()).as("soirée affichée sur le planning").isTrue();
        return matcher.group(1);
    }

    @Test
    void a_new_volunteer_registers_from_the_planning(CapturedOutput output) throws Exception {
        var computer = new Browser(mockMvc);
        var email = Browser.uniqueEmail();

        // 1. Le planning propose de s'inscrire sur la soirée, encore vide.
        var planning = computer.get("/planning").getContentAsString();
        assertThat(statusOnPlanning(planning)).isEqualTo("empty");
        assertThat(planning).contains("href=\"/inscription?soiree=" + EVENING + "\"");

        // 2. Choix d'un type encore disponible.
        var choicePage = computer.get("/inscription?soiree=" + EVENING).getContentAsString();
        assertThat(choicePage).contains(LABEL).contains("value=\"NIGHT_AND_DINNER\"")
                .contains("action=\"/connexion\"");

        // 3. Demande du lien, le choix est transmis.
        var loginPage = computer.get("/connexion?soiree=" + EVENING + "&choix=NIGHT_AND_DINNER").getContentAsString();
        assertThat(loginPage).contains("name=\"soiree\" value=\"" + EVENING + "\"")
                .contains("name=\"choix\" value=\"NIGHT_AND_DINNER\"");
        assertThat(computer.post("/connexion", "email", email, "soiree", EVENING.toString(),
                "choix", "NIGHT_AND_DINNER").getRedirectedUrl()).isEqualTo("/connexion/envoye");
        var link = sender.lastSentTo(email).orElseThrow();
        assertThat(link.link().getQuery()).contains("soiree=" + EVENING).contains("choix=NIGHT_AND_DINNER");

        // 4. Le lien est ouvert sur le téléphone : le choix le suit.
        var phone = computer.otherDevice();
        var confirmLogin = phone.get(link.link().getRawPath() + "?" + link.link().getRawQuery()).getContentAsString();
        assertThat(confirmLogin).contains("name=\"soiree\" value=\"" + EVENING + "\"");
        var afterLogin = phone.post("/login/ott", "token", link.token(), "soiree", EVENING.toString(),
                "choix", "NIGHT_AND_DINNER");
        assertThat(afterLogin.getRedirectedUrl()).isEqualTo("/profil?soiree=" + EVENING + "&choix=NIGHT_AND_DINNER");

        // 5. Premier profil.
        phone.get(afterLogin.getRedirectedUrl());
        var afterProfile = phone.post("/profil", "lastName", "Martin", "firstName", "Anne",
                "phone", "06 00 00 00 01", "soiree", EVENING.toString(), "choix", "NIGHT_AND_DINNER");
        assertThat(afterProfile.getRedirectedUrl())
                .isEqualTo("/inscription/confirmer?soiree=" + EVENING + "&choix=NIGHT_AND_DINNER");

        // 6. Confirmation par POST.
        var confirmPage = phone.get(afterProfile.getRedirectedUrl()).getContentAsString();
        assertThat(confirmPage).contains(LABEL).contains("Passer la nuit");
        var afterConfirm = phone.post("/inscription", "soiree", EVENING.toString(), "choix", "NIGHT_AND_DINNER");
        assertThat(afterConfirm.getRedirectedUrl()).isEqualTo("/inscription/confirmee");
        assertThat(phone.get("/inscription/confirmee").getContentAsString())
                .contains("Inscription confirmée").contains(LABEL);

        // 7. « Mes inscriptions » et le planning reflètent l'inscription.
        assertThat(phone.get("/mes-inscriptions").getContentAsString()).contains(LABEL).contains("Passer la nuit");
        assertThat(jdbc.queryForObject("SELECT phone FROM volunteer WHERE email = ?", String.class, email))
                .isEqualTo("+33600000001");
        var updated = phone.get("/planning").getContentAsString();
        assertThat(statusOnPlanning(updated)).isEqualTo("partial");
        assertThat(updated).contains("Vous participez");
        assertThat(statusOnPlanning(new Browser(mockMvc).get("/planning").getContentAsString()))
                .isEqualTo("partial");

        // 8. Aucune donnée personnelle ni jeton dans les logs (CLAUDE.md, menace M13).
        assertThat(output.getAll()).doesNotContain(email).doesNotContain("@example.org")
                .doesNotContain(link.token()).doesNotContain("Martin").doesNotContain("600000001");
    }

    @Test
    void an_existing_volunteer_goes_straight_to_the_confirmation(CapturedOutput output) throws Exception {
        var email = Browser.uniqueEmail();
        var first = new Browser(mockMvc);
        first.get("/connexion");
        first.post("/connexion", "email", email);
        var firstToken = sender.lastSentTo(email).orElseThrow().token();
        first.get("/login/ott?token=" + firstToken);
        first.post("/login/ott", "token", firstToken);
        first.get("/profil");
        first.post("/profil", "lastName", "Martin", "firstName", "Anne", "phone", "0600000001");

        var browser = new Browser(mockMvc);
        browser.get("/connexion?soiree=" + EVENING + "&choix=BREAKFAST");
        browser.post("/connexion", "email", email, "soiree", EVENING.toString(), "choix", "BREAKFAST");
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);

        var afterLogin = browser.post("/login/ott", "token", token, "soiree", EVENING.toString(), "choix", "BREAKFAST");

        assertThat(afterLogin.getRedirectedUrl())
                .isEqualTo("/inscription/confirmer?soiree=" + EVENING + "&choix=BREAKFAST");
        assertThat(output.getAll()).doesNotContain(email).doesNotContain(token);
    }

    @Test
    void a_signed_in_volunteer_chooses_and_confirms_without_a_new_link() throws Exception {
        var browser = signedInWithProfile();

        var choicePage = browser.get("/inscription?soiree=" + EVENING).getContentAsString();

        assertThat(choicePage).contains("action=\"/inscription/confirmer\"");
        browser.get("/inscription/confirmer?soiree=" + EVENING + "&choix=PREPARE_AT_HOME.SIDES");
        assertThat(browser.post("/inscription", "soiree", EVENING.toString(), "choix", "PREPARE_AT_HOME.SIDES")
                .getRedirectedUrl()).isEqualTo("/inscription/confirmee");
    }

    @Test
    void a_full_type_is_no_longer_offered_and_its_confirmation_is_refused() throws Exception {
        var first = signedInWithProfile();
        first.get("/inscription/confirmer?soiree=" + EVENING + "&choix=PREPARE_AT_HOME.MAIN_DISH");
        first.post("/inscription", "soiree", EVENING.toString(), "choix", "PREPARE_AT_HOME.MAIN_DISH");

        var second = signedInWithProfile();
        var choicePage = second.get("/inscription?soiree=" + EVENING).getContentAsString();
        assertThat(choicePage).doesNotContain("value=\"PREPARE_AT_HOME.MAIN_DISH\"")
                .contains("value=\"PREPARE_AT_HOME.SIDES\"");

        second.get("/inscription/confirmer?soiree=" + EVENING + "&choix=PREPARE_AND_SHARE.MAIN_DISH");
        var refused = second.post("/inscription", "soiree", EVENING.toString(), "choix", "PREPARE_AND_SHARE.MAIN_DISH");

        assertThat(refused.getRedirectedUrl()).isEqualTo("/inscription?soiree=" + EVENING + "&refus=DISH_TAKEN");
        assertThat(second.get(refused.getRedirectedUrl()).getContentAsString())
                .contains("Ce plat est déjà préparé par quelqu’un d’autre");
    }

    @Test
    void a_volunteer_cannot_register_twice_on_the_same_evening() throws Exception {
        var browser = signedInWithProfile();
        browser.get("/inscription/confirmer?soiree=" + EVENING + "&choix=BREAKFAST");
        browser.post("/inscription", "soiree", EVENING.toString(), "choix", "BREAKFAST");

        browser.get("/inscription/confirmer?soiree=" + EVENING + "&choix=NIGHT_AND_DINNER");
        var refused = browser.post("/inscription", "soiree", EVENING.toString(), "choix", "NIGHT_AND_DINNER");

        assertThat(refused.getRedirectedUrl())
                .isEqualTo("/inscription?soiree=" + EVENING + "&refus=ALREADY_REGISTERED");
        assertThat(browser.get("/inscription?soiree=" + EVENING).getContentAsString())
                .contains("Vous participez déjà à cette soirée");
    }

    @Test
    void an_unknown_evening_is_not_found() throws Exception {
        var browser = new Browser(mockMvc);

        assertThat(browser.get("/inscription?soiree=2030-01-01").getStatus()).isEqualTo(404);
        assertThat(browser.get("/inscription?soiree=pas-une-date").getStatus()).isEqualTo(404);
    }

    @Test
    void a_past_evening_cannot_be_chosen() throws Exception {
        // L'horloge de test est au 4 janvier 2027 : la soirée du 3 n'existe pas, celle du 4 est encore ouverte.
        var page = new Browser(mockMvc).get("/inscription?soiree=2027-01-04").getContentAsString();

        assertThat(page).contains("value=\"BREAKFAST\"");
    }

    private Browser signedInWithProfile() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        browser.get("/profil");
        browser.post("/profil", "lastName", "Martin", "firstName", "Anne", "phone", "0600000001");
        return browser;
    }
}
