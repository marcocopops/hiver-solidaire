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
import org.springframework.test.web.servlet.MockMvc;

/** Session de 30 jours sur l'appareil (§6 étape 6, §8 « Cookie », ADR-0007). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class SessionTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void resetClock() {
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
    }

    private Browser loggedIn() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        assertThat(browser.post("/login/ott", "token", token).getRedirectedUrl()).isEqualTo("/profil");
        return browser;
    }

    @Test
    void session_cookie_is_host_prefixed_secure_http_only_and_same_site_lax() throws Exception {
        var browser = new Browser(mockMvc);
        var response = browser.get("/connexion");

        var header = response.getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(Browser.SESSION_COOKIE + "=")).findFirst().orElseThrow();
        assertThat(header).contains("Path=/").contains("Secure").contains("HttpOnly").contains("SameSite=Lax")
                .contains("Max-Age=2592000").doesNotContainIgnoringCase("Domain=");
    }

    @Test
    void login_issues_a_new_session_identifier() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        var before = browser.sessionCookie().getValue();

        browser.post("/login/ott", "token", token);

        assertThat(browser.sessionCookie().getValue()).isNotEqualTo(before);
    }

    @Test
    void session_is_stored_in_the_database_for_30_days_under_a_technical_identifier() throws Exception {
        loggedIn();

        var rows = jdbc.queryForList("SELECT principal_name, max_inactive_interval FROM spring_session "
                + "WHERE principal_name IS NOT NULL");
        assertThat(rows).isNotEmpty().allSatisfy(row -> {
            assertThat(row.get("max_inactive_interval")).isEqualTo((int) Duration.ofDays(30).toSeconds());
            assertThat((String) row.get("principal_name"))
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        });
    }

    @Test
    void session_is_kept_beyond_8_hours() throws Exception {
        var browser = loggedIn();

        clock.advance(Duration.ofHours(9));

        assertThat(browser.get("/profil").getStatus()).isEqualTo(200);
    }

    @Test
    void session_is_kept_until_30_days() throws Exception {
        var browser = loggedIn();

        clock.advance(Duration.ofDays(30).minusMinutes(1));

        assertThat(browser.get("/profil").getStatus()).isEqualTo(200);
    }

    @Test
    void session_is_refused_beyond_30_days_even_if_used_every_day() throws Exception {
        var browser = loggedIn();
        for (int day = 0; day < 30; day++) {
            clock.advance(Duration.ofDays(1));
            browser.get("/profil");
        }

        var response = browser.get("/profil");

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).endsWith("/connexion");
        assertThat(browser.get("/profil").getRedirectedUrl()).endsWith("/connexion");
    }
}
