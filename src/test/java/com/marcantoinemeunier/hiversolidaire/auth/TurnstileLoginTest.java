package com.marcantoinemeunier.hiversolidaire.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.StubTurnstile;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.auth.Turnstile.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Turnstile sur la demande de lien (cahier des charges §6 étape 2, §8 ; ADR-0013 ; menaces M3, M4, M5) : aucun jeton
 * n'est créé ni aucun e-mail envoyé sans vérification réussie, une absence de réponse de Cloudflare ne laisse pas
 * passer, et la réponse ne dit jamais si l'adresse est connue.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class TurnstileLoginTest {

    private static final String FAILED = "La vérification anti-robot n’a pas abouti";
    private static final String UNAVAILABLE = "La vérification anti-robot est momentanément impossible";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    StubTurnstile turnstile;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void reset() {
        turnstile.reset();
    }

    private int tokensFor(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM one_time_token WHERE username = ?", Integer.class, email);
    }

    private static Browser onLoginPage(Browser browser) throws Exception {
        browser.get("/connexion");
        return browser;
    }

    @Test
    void the_login_page_shows_the_widget_with_the_public_site_key() throws Exception {
        var page = new Browser(mockMvc).get("/connexion").getContentAsString();

        assertThat(page).contains("class=\"cf-turnstile ")
                .contains("data-sitekey=\"" + StubTurnstile.SITE_KEY + "\"")
                .contains("<script src=\"https://challenges.cloudflare.com/turnstile/v0/api.js\" async defer>")
                .contains("<noscript>");
    }

    @Test
    void a_valid_token_lets_the_link_go_and_cloudflare_gets_the_client_ip() throws Exception {
        var browser = onLoginPage(new Browser(mockMvc));
        var email = Browser.uniqueEmail();

        assertThat(browser.requestLink(email).getRedirectedUrl()).isEqualTo("/connexion/envoye");

        assertThat(sender.sentTo(email)).hasSize(1);
        assertThat(turnstile.callsFrom(browser.remoteAddress())).singleElement()
                .isEqualTo(new StubTurnstile.Call(Browser.TURNSTILE_TOKEN, browser.remoteAddress()));
    }

    @Test
    void without_a_token_nothing_is_sent_and_cloudflare_is_not_called() throws Exception {
        var browser = onLoginPage(new Browser(mockMvc));
        var email = Browser.uniqueEmail();

        var response = browser.post("/connexion", "email", email);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains(FAILED);
        assertThat(sender.sentTo(email)).isEmpty();
        assertThat(tokensFor(email)).isZero();
        assertThat(turnstile.callsFrom(browser.remoteAddress())).isEmpty();
    }

    @Test
    void a_refused_token_sends_nothing() throws Exception {
        var browser = onLoginPage(new Browser(mockMvc));
        var email = Browser.uniqueEmail();

        var response = browser.post("/connexion", "email", email, Browser.TURNSTILE_FIELD, "jeton-refuse");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains(FAILED).contains("value=\"" + email + "\"");
        assertThat(sender.sentTo(email)).isEmpty();
        assertThat(tokensFor(email)).isZero();
    }

    @Test
    void when_cloudflare_does_not_answer_the_request_is_refused() throws Exception {
        turnstile.respondWith(Outcome.UNAVAILABLE);
        var browser = onLoginPage(new Browser(mockMvc));
        var email = Browser.uniqueEmail();

        var response = browser.requestLink(email);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains(UNAVAILABLE);
        assertThat(sender.sentTo(email)).isEmpty();
        assertThat(tokensFor(email)).isZero();
    }

    @Test
    void the_answer_is_the_same_for_a_known_and_an_unknown_address() throws Exception {
        var known = new TestVolunteers(mockMvc, sender, coordinators, jdbc).volunteer().email();
        var unknown = Browser.uniqueEmail();

        assertSame(onLoginPage(new Browser(mockMvc)).requestLink(known),
                onLoginPage(new Browser(mockMvc)).requestLink(unknown));

        turnstile.respondWith(Outcome.FAILED);
        assertSame(onLoginPage(new Browser(mockMvc)).requestLink(known),
                onLoginPage(new Browser(mockMvc)).requestLink(unknown));

        turnstile.respondWith(Outcome.UNAVAILABLE);
        assertSame(onLoginPage(new Browser(mockMvc)).requestLink(known),
                onLoginPage(new Browser(mockMvc)).requestLink(unknown));
    }

    private static void assertSame(MockHttpServletResponse known, MockHttpServletResponse unknown) throws Exception {
        assertThat(known.getStatus()).isEqualTo(unknown.getStatus());
        assertThat(known.getRedirectedUrl()).isEqualTo(unknown.getRedirectedUrl());
        assertThat(normalize(known.getContentAsString())).isEqualTo(normalize(unknown.getContentAsString()));
    }

    /** Page sans ce qui change d'une requête à l'autre : l'adresse saisie, réaffichée, et le jeton CSRF. */
    private static String normalize(String page) {
        return page.replaceAll("value=\"[^\"]*@example\\.org\"", "value=\"\"")
                .replaceAll("name=\"_csrf\"[^>]*value=\"[^\"]+\"", "name=\"_csrf\"");
    }

    @Test
    void without_turnstile_keys_login_is_unavailable() throws Exception {
        // Jeton CSRF lu pendant que la connexion était encore disponible (le formulaire disparaît ensuite).
        var browser = onLoginPage(new Browser(mockMvc));
        turnstile.setConfigured(false);

        var page = browser.get("/connexion").getContentAsString();
        assertThat(page).contains("momentanément indisponible").doesNotContain("cf-turnstile")
                .doesNotContain("challenges.cloudflare.com");

        var email = Browser.uniqueEmail();
        var response = browser.requestLink(email);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(sender.sentTo(email)).isEmpty();
    }
}
