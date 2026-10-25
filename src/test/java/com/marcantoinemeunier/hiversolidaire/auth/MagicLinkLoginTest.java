package com.marcantoinemeunier.hiversolidaire.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Connexion par lien magique (cahier des charges §6 étapes 2-3, §8 ; ADR-0003 ; menaces M1, M2, M3, M5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class MagicLinkLoginTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void reset() {
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        sender.setAvailable(true);
    }

    /** Demande un lien depuis un navigateur et renvoie le jeton reçu. */
    private String requestLink(Browser browser, String email) throws Exception {
        browser.get("/connexion");
        var response = browser.post("/connexion", "email", email);
        assertThat(response.getRedirectedUrl()).isEqualTo("/connexion/envoye");
        return sender.lastSentTo(email).orElseThrow().token();
    }

    private int storedTokens() {
        return jdbc.queryForObject("SELECT count(*) FROM one_time_token", Integer.class);
    }

    private boolean authenticated(Browser browser) throws Exception {
        return browser.get("/mes-inscriptions").getStatus() == 200
                || browser.get("/profil").getStatus() == 200;
    }

    @Test
    void the_link_points_to_the_confirmation_page_of_the_configured_origin() throws Exception {
        var email = Browser.uniqueEmail();
        requestLink(new Browser(mockMvc), email);

        var link = sender.lastSentTo(email).orElseThrow().link();
        assertThat(link.toString()).startsWith("http://localhost:8080/login/ott?token=");
    }

    @Test
    void opening_the_link_shows_a_confirmation_page_without_logging_in_nor_consuming_the_token() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        var before = storedTokens();

        var page = browser.get("/login/ott?token=" + token);

        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getContentAsString()).contains("Me connecter").contains("method=\"post\"")
                .contains("name=\"token\"").contains("name=\"_csrf\"");
        assertThat(storedTokens()).isEqualTo(before);
        assertThat(authenticated(browser)).isFalse();
    }

    @Test
    void the_confirmation_page_shows_the_masked_address_of_the_link() throws Exception {
        // Parade à la connexion au compte d'un autre : un lien reçu d'un tiers affiche une adresse inconnue.
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        var token = requestLink(browser, email);

        var page = browser.get("/login/ott?token=" + token).getContentAsString();

        assertThat(page).contains("b•••@example.org").doesNotContain(email)
                .contains("Si ce n’est pas votre adresse");
    }

    @Test
    void an_invalid_link_says_so_at_once_without_a_login_button() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        clock.advance(Duration.ofMinutes(16));

        assertThat(browser.get("/login/ott?token=" + token).getRedirectedUrl())
                .isEqualTo("/connexion?lien-invalide");
        assertThat(new Browser(mockMvc).get("/login/ott?token=inconnu").getRedirectedUrl())
                .isEqualTo("/connexion?lien-invalide");
    }

    @Test
    void posting_the_confirmation_page_logs_in_a_new_volunteer_and_asks_for_the_profile() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        browser.get("/login/ott?token=" + token);

        var response = browser.post("/login/ott", "token", token);

        assertThat(response.getRedirectedUrl()).isEqualTo("/profil");
        assertThat(browser.get("/profil").getStatus()).isEqualTo(200);
    }

    @Test
    void the_link_works_on_another_device_than_the_one_that_asked_for_it() throws Exception {
        var computer = new Browser(mockMvc);
        var token = requestLink(computer, Browser.uniqueEmail());

        var phone = computer.otherDevice();
        phone.get("/login/ott?token=" + token);

        assertThat(phone.post("/login/ott", "token", token).getRedirectedUrl()).isEqualTo("/profil");
    }

    @Test
    void login_without_csrf_token_is_refused_and_keeps_the_token() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        var before = storedTokens();

        var response = browser.postWithoutCsrf("/login/ott", "token", token);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(storedTokens()).isEqualTo(before);
        assertThat(authenticated(browser)).isFalse();
    }

    @Test
    void a_token_cannot_be_used_twice() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);

        var thief = new Browser(mockMvc);
        assertThat(thief.get("/login/ott?token=" + token).getRedirectedUrl()).isEqualTo("/connexion?lien-invalide");
        // Même en postant directement le jeton, avec un jeton CSRF valide, la connexion est refusée.
        thief.get("/connexion");
        var response = thief.post("/login/ott", "token", token);

        assertThat(response.getRedirectedUrl()).isEqualTo("/connexion?lien-invalide");
        assertThat(authenticated(thief)).isFalse();
    }

    @Test
    void an_expired_token_is_refused() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        browser.get("/login/ott?token=" + token);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        var response = browser.post("/login/ott", "token", token);

        assertThat(response.getRedirectedUrl()).isEqualTo("/connexion?lien-invalide");
        assertThat(authenticated(browser)).isFalse();
    }

    @Test
    void invalid_link_page_explains_without_detail() throws Exception {
        var page = new Browser(mockMvc).get("/connexion?lien-invalide");

        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getContentAsString()).contains("Ce lien n’est plus valable");
    }

    @Test
    void the_response_is_the_same_for_a_known_and_an_unknown_address() throws Exception {
        var known = Browser.uniqueEmail();
        var first = new Browser(mockMvc);
        var token = requestLink(first, known);
        first.get("/login/ott?token=" + token);
        first.post("/login/ott", "token", token);

        var forKnown = demand(known);
        var forUnknown = demand(Browser.uniqueEmail());

        assertThat(forKnown.getStatus()).isEqualTo(forUnknown.getStatus()).isEqualTo(302);
        assertThat(forKnown.getRedirectedUrl()).isEqualTo(forUnknown.getRedirectedUrl())
                .isEqualTo("/connexion/envoye");
        var sentPage = new Browser(mockMvc).get("/connexion/envoye").getContentAsString();
        assertThat(sentPage).contains("Si cette adresse est valide").doesNotContain("@example.org");
    }

    private MockHttpServletResponse demand(String email) throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");
        return browser.post("/connexion", "email", email);
    }

    @Test
    void a_malformed_address_is_rejected_with_a_message() throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");

        var response = browser.post("/connexion", "email", "pas-une-adresse");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Saisissez une adresse e-mail valide");
    }

    @Test
    void requests_are_limited_per_address_without_revealing_it() throws Exception {
        var email = Browser.uniqueEmail();
        for (int i = 0; i < 3; i++) {
            assertThat(demand(email).getRedirectedUrl()).isEqualTo("/connexion/envoye");
        }

        assertThat(demand(email).getRedirectedUrl()).isEqualTo("/connexion/envoye");
        assertThat(sender.sentTo(email)).hasSize(3);

        clock.advance(Duration.ofMinutes(15));
        assertThat(demand(email).getRedirectedUrl()).isEqualTo("/connexion/envoye");
        assertThat(sender.sentTo(email)).hasSize(4);
    }

    @Test
    void requests_are_limited_per_ip_address() throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");
        for (int i = 0; i < 10; i++) {
            assertThat(browser.post("/connexion", "email", Browser.uniqueEmail()).getRedirectedUrl())
                    .isEqualTo("/connexion/envoye");
        }

        var refused = Browser.uniqueEmail();
        var response = browser.post("/connexion", "email", refused);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("Trop de demandes");
        assertThat(sender.sentTo(refused)).isEmpty();
    }

    @Test
    void without_smtp_the_login_page_says_it_is_unavailable_and_sends_nothing() throws Exception {
        // Formulaire ouvert avant que l'envoi devienne indisponible : il porte encore un jeton CSRF.
        var browser = new Browser(mockMvc);
        browser.get("/connexion");
        sender.setAvailable(false);
        var page = new Browser(mockMvc).get("/connexion");
        assertThat(page.getContentAsString()).contains("La connexion est momentanément indisponible")
                .doesNotContain("name=\"email\"");

        var email = Browser.uniqueEmail();
        var response = browser.post("/connexion", "email", email);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("La connexion est momentanément indisponible");
        assertThat(sender.sentTo(email)).isEmpty();
    }

    @Test
    void spring_default_token_generation_endpoint_is_closed() throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");
        var email = Browser.uniqueEmail();

        var response = browser.post("/ott/generate", "username", email);

        assertThat(response.getStatus()).isIn(403, 404);
        assertThat(sender.sentTo(email)).isEmpty();
    }

    @Test
    void the_default_spring_submit_page_is_not_served() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        var body = browser.get("/login/ott?token=" + token).getContentAsString();

        assertThat(body).doesNotContain("Please sign in");
    }

    @Test
    void logout_ends_the_session() throws Exception {
        var browser = new Browser(mockMvc);
        var token = requestLink(browser, Browser.uniqueEmail());
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        browser.get("/profil");

        var response = browser.post("/deconnexion");

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(authenticated(browser)).isFalse();
    }
}
