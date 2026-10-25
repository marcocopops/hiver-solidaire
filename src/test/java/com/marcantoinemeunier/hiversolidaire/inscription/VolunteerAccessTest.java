package com.marcantoinemeunier.hiversolidaire.inscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Accès refusé sans connexion, et un bénévole ne voit que ses propres données (menace M9). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class VolunteerAccessTest {

    private static final LocalDate EVENING = LocalDate.of(2027, 1, 8);
    private static final String LABEL = "Ven. 8 → sam. 9 janv.";

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

    private Browser signedIn(boolean withProfile) throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        if (withProfile) {
            browser.get("/profil");
            browser.post("/profil", "lastName", "Martin", "firstName", "Anne", "phone", "0600000001");
        }
        return browser;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/profil",
        "/mes-inscriptions",
        "/inscription/confirmee",
        "/inscription/confirmer?soiree=2027-01-08&choix=BREAKFAST"
    })
    void volunteer_pages_redirect_anonymous_visitors_to_the_login_page(String url) throws Exception {
        var response = new Browser(mockMvc).get(url);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).endsWith("/connexion");
        assertThat(response.getContentAsString()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/profil", "/inscription"})
    void volunteer_actions_are_refused_to_anonymous_visitors(String url) throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");

        var response = browser.post(url, "soiree", EVENING.toString(), "choix", "BREAKFAST",
                "lastName", "Martin", "firstName", "Anne", "phone", "0600000001");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id WHERE e.evening_date = ?
                """, Integer.class, EVENING)).isZero();
    }

    @Test
    void registration_without_csrf_token_is_refused() throws Exception {
        var browser = signedIn(true);

        var response = browser.postWithoutCsrf("/inscription", "soiree", EVENING.toString(), "choix", "BREAKFAST");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void registration_cannot_be_made_by_get() throws Exception {
        var browser = signedIn(true);

        assertThat(browser.get("/inscription?soiree=" + EVENING + "&choix=BREAKFAST").getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id WHERE e.evening_date = ?
                """, Integer.class, EVENING)).isZero();
    }

    @Test
    void a_volunteer_does_not_see_the_registrations_of_another() throws Exception {
        var alice = signedIn(true);
        alice.get("/inscription/confirmer?soiree=" + EVENING + "&choix=BREAKFAST");
        alice.post("/inscription", "soiree", EVENING.toString(), "choix", "BREAKFAST");
        assertThat(alice.get("/mes-inscriptions").getContentAsString()).contains(LABEL);

        var bob = signedIn(true);

        assertThat(bob.get("/mes-inscriptions").getContentAsString()).doesNotContain(LABEL);
        assertThat(bob.get("/planning").getContentAsString()).doesNotContain("Vous participez");
    }

    @Test
    void a_volunteer_without_profile_is_sent_to_the_profile_form_before_registering() throws Exception {
        var browser = signedIn(false);

        var confirm = browser.get("/inscription/confirmer?soiree=" + EVENING + "&choix=BREAKFAST");
        assertThat(confirm.getRedirectedUrl()).isEqualTo("/profil?soiree=" + EVENING + "&choix=BREAKFAST");

        browser.get("/profil");
        var post = browser.post("/inscription", "soiree", EVENING.toString(), "choix", "BREAKFAST");
        assertThat(post.getRedirectedUrl()).isEqualTo("/profil?soiree=" + EVENING + "&choix=BREAKFAST");
        assertThat(browser.get("/mes-inscriptions").getRedirectedUrl()).isEqualTo("/profil");
    }

    @Test
    void a_completed_profile_is_not_editable_here() throws Exception {
        var browser = signedIn(true);

        assertThat(browser.get("/profil").getRedirectedUrl()).isEqualTo("/mes-inscriptions");
    }

    @Test
    void the_profile_form_rejects_invalid_values_server_side() throws Exception {
        var browser = signedIn(false);
        browser.get("/profil");

        var response = browser.post("/profil", "lastName", "<script>", "firstName", "Anne", "phone", "123");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Indiquez votre nom")
                .contains("Indiquez un numéro de téléphone français").doesNotContain("<script>");
        assertThat(browser.get("/mes-inscriptions").getRedirectedUrl()).isEqualTo("/profil");
    }

    @Test
    void every_profile_field_has_a_label_and_recontact_is_unchecked() throws Exception {
        var page = signedIn(false).get("/profil").getContentAsString();

        for (var id : List.of("lastName", "firstName", "phone", "recontactNextSeason")) {
            assertThat(page).contains("for=\"" + id + "\"").contains("id=\"" + id + "\"");
        }
        assertThat(page).doesNotContain("checked");
    }
}
