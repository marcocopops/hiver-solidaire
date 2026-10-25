package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * « Mon compte » (cahier des charges §6) : le bénévole voit ses données, modifie son téléphone et son pseudo,
 * donne ou retire son accord de recontact. Il ne lit et n'écrit que son propre compte (M9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class AccountJourneyTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    JdbcTemplate jdbc;

    record Volunteer(Browser browser, String email, String pseudo) {
    }

    private Volunteer signedIn(boolean withProfile) throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        var pseudo = Browser.uniquePseudo();
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        if (withProfile) {
            browser.get("/profil");
            browser.post("/profil", "pseudo", pseudo, "lastName", "Martin", "firstName", "Anne",
                    "phone", "0600000001");
        }
        return new Volunteer(browser, email, pseudo);
    }

    private String column(String column, String email) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM volunteer WHERE email = ?", String.class, email);
    }

    @Test
    void the_account_page_shows_the_volunteer_data(CapturedOutput output) throws Exception {
        var alice = signedIn(true);

        var page = alice.browser().get("/mon-compte");

        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getContentAsString()).contains(alice.email()).contains("Martin").contains("Anne")
                .contains(alice.pseudo()).contains("+33600000001")
                .contains("ne peut pas être modifiée").contains("public")
                .contains("href=\"/mon-compte/supprimer\"");
        assertThat(output.getAll()).doesNotContain(alice.email()).doesNotContain(alice.pseudo())
                .doesNotContain("+33600000001");
    }

    @Test
    void the_account_link_is_in_the_navigation() throws Exception {
        assertThat(signedIn(true).browser().get("/mes-inscriptions").getContentAsString())
                .contains("href=\"/mon-compte\"");
    }

    @Test
    void a_volunteer_changes_their_phone() throws Exception {
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");

        var response = alice.browser().post("/mon-compte/telephone", "phoneCountryCode", "+32", "phone",
                "0470 00 00 02");

        assertThat(response.getRedirectedUrl()).isEqualTo("/mon-compte");
        assertThat(column("phone", alice.email())).isEqualTo("+32470000002");
        assertThat(alice.browser().get("/mon-compte").getContentAsString()).contains("Modification enregistrée")
                .contains("+32470000002");
    }

    @Test
    void an_invalid_phone_is_rejected_with_the_same_rules_as_the_first_profile() throws Exception {
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");

        var response = alice.browser().post("/mon-compte/telephone", "phoneCountryCode", "+33", "phone", "123");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Indiquez un numéro de téléphone valide");
        assertThat(column("phone", alice.email())).isEqualTo("+33600000001");
    }

    @Test
    void a_volunteer_changes_their_pseudo() throws Exception {
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");
        var renamed = Browser.uniquePseudo();

        var response = alice.browser().post("/mon-compte/pseudo", "pseudo", renamed);

        assertThat(response.getRedirectedUrl()).isEqualTo("/mon-compte");
        assertThat(column("pseudo", alice.email())).isEqualTo(renamed);
    }

    @Test
    void a_volunteer_may_change_the_case_of_their_own_pseudo() throws Exception {
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");

        var response = alice.browser().post("/mon-compte/pseudo", "pseudo", alice.pseudo().toUpperCase());

        assertThat(response.getRedirectedUrl()).isEqualTo("/mon-compte");
        assertThat(column("pseudo", alice.email())).isEqualTo(alice.pseudo().toUpperCase());
    }

    @Test
    void a_pseudo_taken_by_another_volunteer_is_refused_with_a_variant() throws Exception {
        var bob = signedIn(true);
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");

        var response = alice.browser().post("/mon-compte/pseudo", "pseudo", bob.pseudo().toLowerCase());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("déjà pris").contains(bob.pseudo().toLowerCase() + "2");
        assertThat(column("pseudo", alice.email())).isEqualTo(alice.pseudo());
    }

    @Test
    void an_invalid_pseudo_is_rejected_and_not_echoed() throws Exception {
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");

        var response = alice.browser().post("/mon-compte/pseudo", "pseudo", "<b>Anne</b>");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Le pseudo doit faire").doesNotContain("<b>Anne</b>");
        assertThat(column("pseudo", alice.email())).isEqualTo(alice.pseudo());
    }

    @Test
    void a_volunteer_gives_then_withdraws_the_recontact_agreement() throws Exception {
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");

        alice.browser().post("/mon-compte/recontact", "recontactNextSeason", "true");
        assertThat(column("recontact_next_season", alice.email())).isEqualTo("true");

        alice.browser().get("/mon-compte");
        alice.browser().post("/mon-compte/recontact");
        assertThat(column("recontact_next_season", alice.email())).isEqualTo("false");
    }

    @Test
    void changes_only_touch_the_account_of_the_session() throws Exception {
        var bob = signedIn(true);
        var bobId = column("id", bob.email());
        var alice = signedIn(true);
        alice.browser().get("/mon-compte");

        alice.browser().post("/mon-compte/telephone", "id", bobId, "volunteerId", bobId, "phoneCountryCode", "+33",
                "phone", "0700000009");
        alice.browser().post("/mon-compte/recontact", "id", bobId, "recontactNextSeason", "true");

        assertThat(column("phone", bob.email())).isEqualTo("+33600000001");
        assertThat(column("recontact_next_season", bob.email())).isEqualTo("false");
        assertThat(column("phone", alice.email())).isEqualTo("+33700000009");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mon-compte", "/mon-compte/supprimer"})
    void account_pages_redirect_anonymous_visitors_to_the_login_page(String url) throws Exception {
        var response = new Browser(mockMvc).get(url);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).endsWith("/connexion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mon-compte/telephone", "/mon-compte/pseudo", "/mon-compte/recontact",
        "/mon-compte/supprimer"})
    void account_actions_are_refused_to_anonymous_visitors(String url) throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");

        assertThat(browser.post(url, "phoneCountryCode", "+33", "phone", "0600000001", "pseudo", "Intrus")
                .getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mon-compte/telephone", "/mon-compte/pseudo", "/mon-compte/recontact",
        "/mon-compte/supprimer"})
    void account_actions_without_csrf_token_are_refused(String url) throws Exception {
        var alice = signedIn(true);

        var response = alice.browser().postWithoutCsrf(url, "phoneCountryCode", "+33", "phone", "0700000009",
                "pseudo", Browser.uniquePseudo(), "recontactNextSeason", "true");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(column("phone", alice.email())).isEqualTo("+33600000001");
        assertThat(column("pseudo", alice.email())).isEqualTo(alice.pseudo());
        assertThat(column("deleted_at", alice.email())).isNull();
    }

    @Test
    void account_actions_cannot_be_made_by_get() throws Exception {
        var alice = signedIn(true);

        for (var url : new String[] {"/mon-compte/telephone?phoneCountryCode=%2B33&phone=0700000009",
            "/mon-compte/pseudo?pseudo=Intrus", "/mon-compte/recontact?recontactNextSeason=true"}) {
            assertThat(alice.browser().get(url).getStatus()).as(url).isIn(403, 404, 405);
        }
        assertThat(alice.browser().get("/mon-compte/supprimer").getStatus()).isEqualTo(200);
        assertThat(column("phone", alice.email())).isEqualTo("+33600000001");
        assertThat(column("deleted_at", alice.email())).isNull();
    }

    @Test
    void a_volunteer_without_profile_completes_it_first_but_may_delete_the_account() throws Exception {
        var alice = signedIn(false);

        assertThat(alice.browser().get("/mon-compte").getRedirectedUrl()).isEqualTo("/profil");
        assertThat(alice.browser().get("/mon-compte/supprimer").getStatus()).isEqualTo(200);
    }
}
