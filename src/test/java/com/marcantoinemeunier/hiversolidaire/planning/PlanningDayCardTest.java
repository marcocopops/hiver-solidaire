package com.marcantoinemeunier.hiversolidaire.planning;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Carte d'une journée vue par un bénévole connecté (§6) : « Vous » à la place de son pseudo, ce qu'il fait, un
 * bouton de désinscription par inscription active avec la date limite exacte (R6), le filtre « Les miennes ». Rien
 * sur les inscriptions des autres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class PlanningDayCardTest {

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    /** Samedi 9 janvier 2027 : petit-déjeuner de la soirée du 8, dîner et nuit de la soirée du 9. */
    private static final LocalDate DAY = LocalDate.of(2027, 1, 9);
    private static final String DAY_LABEL = "Sam. 9 janv.";
    private static final String DAY_CARD = "<li class=\"journee\"";
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
        for (var date : new LocalDate[] {DAY.minusDays(1), DAY}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    record Volunteer(Browser browser, String pseudo) {
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
        return new Volunteer(browser, pseudo);
    }

    /** Inscription depuis la carte du jour {@code day}. */
    private static void join(Browser browser, LocalDate day, String choice) throws Exception {
        browser.get("/inscription/confirmer?jour=" + day + "&choix=" + choice);
        assertThat(browser.post("/inscription", "jour", day.toString(), "choix", choice).getRedirectedUrl())
                .isEqualTo("/inscription/confirmee");
    }

    private static String dayBlock(String body, String label) {
        var start = body.indexOf(">" + label + "<");
        assertThat(start).as(label).isPositive();
        var end = body.indexOf(DAY_CARD, start);
        return body.substring(start + 1, end < 0 ? body.length() : end);
    }

    private static String text(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").strip();
    }

    private static int count(String body, String text) {
        return body.split(Pattern.quote(text), -1).length - 1;
    }

    @Test
    void the_volunteer_sees_you_instead_of_their_pseudo_and_what_they_do() throws Exception {
        var alice = signedIn();
        join(alice.browser(), DAY, "NIGHT_AND_DINNER");

        var body = alice.browser().get("/planning").getContentAsString();
        var block = text(dayBlock(body, DAY_LABEL));

        assertThat(block).contains("Nuit jusqu’au matin Vous").contains("Vous passez la nuit")
                .contains("Me désinscrire").doesNotContain("Me désinscrire de");
        assertThat(body).doesNotContain(alice.pseudo());
    }

    @Test
    void one_button_per_registration_with_its_exact_deadline() throws Exception {
        var alice = signedIn();
        join(alice.browser(), DAY, "BREAKFAST");
        join(alice.browser(), DAY, "PREPARE_AND_SHARE.MAIN_DISH");

        var block = dayBlock(alice.browser().get("/planning").getContentAsString(), DAY_LABEL);

        assertThat(count(block, "href=\"/mes-inscriptions/annuler?soiree=")).isEqualTo(2);
        assertThat(block).contains("href=\"/mes-inscriptions/annuler?soiree=" + DAY.minusDays(1) + "\"")
                .contains("href=\"/mes-inscriptions/annuler?soiree=" + DAY + "\"");
        assertThat(text(block))
                .contains("Petit-déjeuner Vous")
                .contains("Plat principal Vous")
                .contains("Dîner Vous")
                .containsSubsequence(
                        "Vous fournissez le petit-déjeuner", "Me désinscrire du petit-déjeuner",
                        "Possible jusqu’au jeu. 7 janv. à 23 h 59",
                        "Vous préparez le plat principal et partagez le dîner", "Me désinscrire du dîner",
                        "Possible jusqu’au ven. 8 janv. à 23 h 59");
    }

    @Test
    void after_the_deadline_the_button_gives_way_to_the_coordinators_phone() throws Exception {
        var alice = signedIn();
        join(alice.browser(), DAY, "BREAKFAST");
        join(alice.browser(), DAY, "NIGHT_AND_DINNER");
        // Vendredi 8 janvier : la limite du petit-déjeuner (jeudi 7, 23 h 59) est passée, pas celle de la nuit.
        clock.set(LocalDateTime.of(2027, 1, 8, 9, 0).atZone(ParisTime.ZONE).toInstant());

        var block = dayBlock(alice.browser().get("/planning").getContentAsString(), DAY_LABEL);

        assertThat(block).doesNotContain("href=\"/mes-inscriptions/annuler?soiree=" + DAY.minusDays(1) + "\"")
                .contains("href=\"/mes-inscriptions/annuler?soiree=" + DAY + "\"")
                .contains("href=\"tel:0199001234\"").contains(COORDINATORS_PHONE);
        assertThat(text(block)).containsSubsequence("Vous fournissez le petit-déjeuner", COORDINATORS_PHONE,
                "Vous passez la nuit", "Me désinscrire de la nuit");
    }

    @Test
    void the_button_leads_to_the_existing_confirmation_page_and_a_get_cancels_nothing() throws Exception {
        var alice = signedIn();
        join(alice.browser(), DAY, "BREAKFAST");

        var confirm = alice.browser().get("/mes-inscriptions/annuler?soiree=" + DAY.minusDays(1));

        assertThat(confirm.getStatus()).isEqualTo(200);
        assertThat(confirm.getContentAsString()).contains("Petit-déjeuner du sam. 9 janv.")
                .contains("action=\"/mes-inscriptions/annuler\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id "
                + "WHERE e.evening_date = ? AND r.cancelled_at IS NULL", Integer.class, DAY.minusDays(1)))
                .isEqualTo(1);
    }

    @Test
    void another_volunteer_sees_the_pseudo_and_no_cancel_button() throws Exception {
        var alice = signedIn();
        join(alice.browser(), DAY, "NIGHT_AND_DINNER");
        var bob = signedIn();

        var body = bob.browser().get("/planning").getContentAsString();
        var block = dayBlock(body, DAY_LABEL);

        assertThat(text(block)).contains("Nuit jusqu’au matin " + alice.pseudo()).doesNotContain("Vous");
        assertThat(body).doesNotContain("/mes-inscriptions/annuler").doesNotContain("Me désinscrire");
        assertThat(UUID.matcher(body).find()).as("identifiant technique").isFalse();
    }

    @Test
    void the_mine_filter_keeps_only_the_days_of_the_volunteer() throws Exception {
        var alice = signedIn();
        join(alice.browser(), DAY, "BREAKFAST");

        var all = alice.browser().get("/planning").getContentAsString();
        var mine = alice.browser().get("/planning?filtre=miennes").getContentAsString();

        assertThat(all).contains("<a href=\"/planning?filtre=miennes\">Les miennes</a>");
        assertThat(mine).contains("<a href=\"/planning?filtre=miennes\" aria-current=\"page\">Les miennes</a>");
        assertThat(count(mine, DAY_CARD)).isEqualTo(1);
        assertThat(mine).contains(">" + DAY_LABEL + "<");
    }

    @Test
    void the_mine_filter_is_empty_without_registration() throws Exception {
        var body = signedIn().browser().get("/planning?filtre=miennes").getContentAsString();

        assertThat(count(body, DAY_CARD)).isZero();
        assertThat(body).contains("Aucune journée pour ce filtre.");
    }

    @Test
    void the_volunteer_can_still_join_the_other_evening_of_a_day() throws Exception {
        var alice = signedIn();
        join(alice.browser(), DAY, "NIGHT_AND_DINNER");

        var block = dayBlock(alice.browser().get("/planning").getContentAsString(), DAY_LABEL);

        assertThat(block).contains("href=\"/inscription?jour=" + DAY + "\"");
    }
}
