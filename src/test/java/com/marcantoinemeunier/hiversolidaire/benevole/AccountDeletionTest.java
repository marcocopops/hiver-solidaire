package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
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
 * Suppression du compte (§6 « Mon compte », §7) : page de confirmation, puis POST. Les inscriptions à venir sont
 * annulées, l'historique est anonymisé, toutes les sessions sont invalidées (ADR-0007), et plus aucune donnée
 * personnelle ne reste en base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class AccountDeletionTest {

    /** Soirées de la saison d'exemple, autour du 6 janvier 2027 (jour de la suppression). */
    private static final LocalDate PAST = LocalDate.of(2027, 1, 5);
    private static final LocalDate TONIGHT = LocalDate.of(2027, 1, 6);
    private static final LocalDate SOON = LocalDate.of(2027, 1, 9);
    private static final LocalDate[] EVENINGS = {PAST, TONIGHT, SOON};
    private static final Instant DELETION_DAY = LocalDateTime.of(2027, 1, 6, 10, 0).atZone(ParisTime.ZONE).toInstant();

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
        for (var date : EVENINGS) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    record Volunteer(Browser browser, String email, String pseudo, UUID id) {
    }

    private Browser login(String email) throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        return browser;
    }

    private Volunteer signedIn(boolean withProfile) throws Exception {
        var email = Browser.uniqueEmail();
        var pseudo = Browser.uniquePseudo();
        var browser = login(email);
        if (withProfile) {
            browser.get("/profil");
            browser.post("/profil", "pseudo", pseudo, "lastName", "Delacourt", "firstName", "Josiane",
                    "phone", "0600000042", "recontactNextSeason", "true");
        }
        var id = jdbc.queryForObject("SELECT id FROM volunteer WHERE email = ?", UUID.class, email);
        return new Volunteer(browser, email, pseudo, id);
    }

    private static void register(Browser browser, LocalDate date, String choice) throws Exception {
        // Inscription depuis la journée de l'activité : le lendemain de la soirée pour un petit-déjeuner.
        var day = "BREAKFAST".equals(choice) ? date.plusDays(1) : date;
        browser.get("/inscription/confirmer?jour=" + day + "&choix=" + choice);
        assertThat(browser.post("/inscription", "jour", day.toString(), "choix", choice).getRedirectedUrl())
                .isEqualTo("/inscription/confirmee");
    }

    private static void delete(Browser browser) throws Exception {
        assertThat(browser.get("/mon-compte/supprimer").getStatus()).isEqualTo(200);
        assertThat(browser.post("/mon-compte/supprimer").getRedirectedUrl()).isEqualTo("/compte-supprime");
    }

    private String registrationState(UUID volunteerId, LocalDate date) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN r.cancelled_at IS NULL THEN 'active' ELSE 'cancelled' END
                FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.volunteer_id = ? AND e.evening_date = ?
                """, String.class, volunteerId, date);
    }

    private int alerts(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id
                WHERE e.evening_date = ?
                """, Integer.class, date);
    }

    @Test
    void deleting_the_account_leaves_no_personal_data(CapturedOutput output) throws Exception {
        var alice = signedIn(true);
        register(alice.browser(), PAST, "BREAKFAST");
        register(alice.browser(), SOON, "NIGHT_AND_DINNER");
        clock.set(DELETION_DAY);
        register(alice.browser(), TONIGHT, "BREAKFAST");
        // Un lien de connexion en cours de validité contient l'adresse.
        jdbc.update("INSERT INTO one_time_token (token_hash, username, expires_at) VALUES (?, ?, ?)",
                "0".repeat(64), alice.email(), Timestamp.from(DELETION_DAY.plusSeconds(600)));
        var otherDevice = login(alice.email());
        assertThat(otherDevice.get("/mes-inscriptions").getStatus()).isEqualTo(200);

        delete(alice.browser());

        // Aucune donnée personnelle : la ligne ne garde que son identifiant et des horodatages.
        assertThat(jdbc.queryForMap("""
                SELECT email, last_name, first_name, phone, pseudo, profile_completed_at, recontact_next_season
                FROM volunteer WHERE id = ?
                """, alice.id())).containsOnly(
                        entry("email", null), entry("last_name", null),
                        entry("first_name", null), entry("phone", null),
                        entry("pseudo", null), entry("profile_completed_at", null),
                        entry("recontact_next_season", false));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM volunteer
                WHERE email = ? OR lower(pseudo) = lower(?) OR phone = '+33600000042'
                    OR last_name = 'Delacourt' OR first_name = 'Josiane'
                """, Integer.class, alice.email(), alice.pseudo())).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM volunteer WHERE id = ?", Timestamp.class,
                alice.id())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM one_time_token WHERE username = ?", Integer.class,
                alice.email())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Integer.class, alice.id().toString())).isZero();

        // Inscriptions : la passée reste comptée, anonyme ; les autres sont annulées, même après la limite de R6.
        assertThat(registrationState(alice.id(), PAST)).isEqualTo("active");
        assertThat(registrationState(alice.id(), TONIGHT)).isEqualTo("cancelled");
        assertThat(registrationState(alice.id(), SOON)).isEqualTo("cancelled");
        assertThat(alerts(TONIGHT)).isEqualTo(1);
        assertThat(alerts(SOON)).isEqualTo(1);

        // Toutes les sessions sont invalidées : celle-ci comme celle de l'autre appareil.
        assertThat(alice.browser().get("/mes-inscriptions").getRedirectedUrl()).endsWith("/connexion");
        assertThat(otherDevice.get("/mes-inscriptions").getRedirectedUrl()).endsWith("/connexion");

        // Le pseudo disparaît du planning.
        assertThat(new Browser(mockMvc).get("/planning").getContentAsString()).doesNotContain(alice.pseudo());

        assertThat(output.getAll()).doesNotContain(alice.email()).doesNotContain(alice.pseudo())
                .doesNotContain("Delacourt").doesNotContain("Josiane").doesNotContain("+33600000042");
    }

    @Test
    void the_deleted_pseudo_can_be_taken_by_someone_else() throws Exception {
        var alice = signedIn(true);
        delete(alice.browser());

        var bob = signedIn(true);
        bob.browser().get("/mon-compte");
        bob.browser().post("/mon-compte/pseudo", "pseudo", alice.pseudo());

        assertThat(jdbc.queryForObject("SELECT pseudo FROM volunteer WHERE id = ?", String.class, bob.id()))
                .isEqualTo(alice.pseudo());
    }

    @Test
    void signing_in_again_with_the_same_address_creates_a_new_account() throws Exception {
        var alice = signedIn(true);
        delete(alice.browser());

        var again = login(alice.email());

        assertThat(again.get("/mes-inscriptions").getRedirectedUrl()).isEqualTo("/profil");
        assertThat(jdbc.queryForObject("SELECT id FROM volunteer WHERE email = ?", UUID.class, alice.email()))
                .isNotEqualTo(alice.id());
    }

    @Test
    void the_confirmation_page_explains_and_deletes_nothing() throws Exception {
        var alice = signedIn(true);
        register(alice.browser(), SOON, "BREAKFAST");

        var page = alice.browser().get("/mon-compte/supprimer").getContentAsString();

        assertThat(page).contains("action=\"/mon-compte/supprimer\"").contains("1 inscription à venir");
        assertThat(jdbc.queryForObject("SELECT email FROM volunteer WHERE id = ?", String.class, alice.id()))
                .isEqualTo(alice.email());
        assertThat(registrationState(alice.id(), SOON)).isEqualTo("active");
    }

    @Test
    void the_confirmation_page_warns_about_evenings_past_the_deadline() throws Exception {
        var alice = signedIn(true);
        clock.set(DELETION_DAY);
        register(alice.browser(), TONIGHT, "BREAKFAST");

        assertThat(alice.browser().get("/mon-compte/supprimer").getContentAsString())
                .contains("01 99 00 12 34");
    }

    @Test
    void an_account_without_profile_can_be_deleted() throws Exception {
        var alice = signedIn(false);

        delete(alice.browser());

        assertThat(jdbc.queryForObject("SELECT email FROM volunteer WHERE id = ?", String.class, alice.id())).isNull();
    }

    @Test
    void the_end_page_is_public_and_signed_out() throws Exception {
        var alice = signedIn(true);
        delete(alice.browser());

        var page = alice.browser().get("/compte-supprime");

        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getContentAsString()).contains("Votre compte a été supprimé")
                .doesNotContain("Me déconnecter");
    }
}
