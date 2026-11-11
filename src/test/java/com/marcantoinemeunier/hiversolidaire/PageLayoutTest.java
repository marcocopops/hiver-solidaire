package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pages bénévoles hors formulaires : en-tête commun avec un seul titre, actions en boutons pilule (principale,
 * secondaire ou définitive). Les formulaires sont couverts par {@link FormAccessibilityTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class PageLayoutTest {

    /** Mercredi 13 janvier 2027 : soirée ouverte aux inscriptions, loin de la limite de désinscription. */
    private static final LocalDate EVENING = LocalDate.of(2027, 1, 13);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                EVENING);
        jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                EVENING);
    }

    private Browser signedIn() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        browser.requestLink(email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        browser.get("/profil");
        browser.post("/profil", "pseudo", Browser.uniquePseudo(), "lastName", "Martin", "firstName", "Anne",
                "phone", "0600000001");
        return browser;
    }

    private static void assertCommonHeader(String html, String title) {
        assertThat(html).contains("<header class=\"entete\">").contains("<h1>" + title + "</h1>");
        assertThat(html.split("<h1", -1)).as("un seul titre").hasSize(2);
    }

    @Test
    void the_home_page_has_the_header_and_leads_to_the_planning() throws Exception {
        var html = new Browser(mockMvc).get("/").getContentAsString();

        assertCommonHeader(html, "Planning des bénévoles");
        assertThat(html).contains("<title>Accueil · Hiver Solidaire</title>")
                .contains("<a class=\"bouton\" href=\"/planning\">Voir le planning</a>");
    }

    @Test
    void the_public_pages_have_the_header() throws Exception {
        var browser = new Browser(mockMvc);

        assertCommonHeader(browser.get("/donnees-personnelles").getContentAsString(), "Vos données personnelles");
        assertCommonHeader(browser.get("/compte-supprime").getContentAsString(), "Compte supprimé");
    }

    @Test
    void my_registrations_and_the_cancellation_page_use_a_secondary_button_to_cancel() throws Exception {
        var browser = signedIn();
        browser.get("/inscription/confirmer?jour=" + EVENING + "&choix=NIGHT_AND_DINNER");
        browser.post("/inscription", "jour", EVENING.toString(), "choix", "NIGHT_AND_DINNER");

        var mine = browser.get("/mes-inscriptions").getContentAsString();
        assertCommonHeader(mine, "Mes inscriptions");
        assertThat(mine).contains("class=\"bouton bouton--secondaire\" href=\"/mes-inscriptions/annuler?soiree=");

        var cancel = browser.get("/mes-inscriptions/annuler?soiree=" + EVENING + "&choix=NIGHT_AND_DINNER")
                .getContentAsString();
        assertCommonHeader(cancel, "Me désinscrire");
        assertThat(cancel).contains("<button class=\"bouton\" type=\"submit\">")
                .contains("<a class=\"bouton bouton--secondaire\" href=\"/mes-inscriptions\">");
    }

    @Test
    void the_account_deletion_keeps_a_danger_button_and_a_way_back() throws Exception {
        var html = signedIn().get("/mon-compte/supprimer").getContentAsString();

        assertCommonHeader(html, "Supprimer mon compte");
        assertThat(html).contains("<button class=\"bouton bouton--danger\" type=\"submit\">")
                .contains("<a class=\"bouton bouton--secondaire\" href=\"/mon-compte\">");
    }
}
