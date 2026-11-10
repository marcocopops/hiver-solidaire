package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Exports (§6, §8) : tableau de la période à l'écran (page et fragment htmx, non audités), page imprimable et CSV
 * (POST audités) ; 7 prochains jours par défaut, 31 jours au plus.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class ExportTest {

    /** Mardi 5 et samedi 9 janvier 2027 : dans les 7 jours qui suivent l'horloge de test (lundi 4). */
    private static final LocalDate SOON = LocalDate.of(2027, 1, 5);
    private static final LocalDate WEEKEND = LocalDate.of(2027, 1, 9);
    private static final LocalDate LATER = LocalDate.of(2027, 2, 24);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    JdbcTemplate jdbc;

    TestVolunteers volunteers;

    @BeforeEach
    void setUp() {
        volunteers = new TestVolunteers(mockMvc, sender, coordinators, jdbc);
    }

    @AfterEach
    void clean() {
        // Un petit-déjeuner inscrit depuis la journée D appartient à la soirée D-1 (ADR-0009) : on nettoie les deux.
        for (var date : new LocalDate[] {SOON, WEEKEND.minusDays(1), WEEKEND, LATER}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    private int audits(UUID coordinatorId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = ?",
                Integer.class, coordinatorId, action);
    }

    /** Bénévole sans e-mail au nom qui ressemble à une formule de tableur. */
    private UUID formulaVolunteer(String pseudo) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, last_name, first_name, phone, pseudo, profile_completed_at, reviewed_at)
                VALUES (?, '=HYPERLINK("http://example.org")', '@Anne', '+33600000009', ?, now(), now())
                """, id, pseudo);
        return id;
    }

    @Test
    void the_csv_contains_the_registrations_of_the_period_with_formulas_neutralised(CapturedOutput output)
            throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        TestVolunteers.register(anne.browser(), LATER, "BREAKFAST");
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        var pseudo = Browser.uniquePseudo();
        var formula = formulaVolunteer(pseudo);
        browser.get("/admin/inscriptions/nouvelle?jour=" + WEEKEND + "&benevole=" + formula + "&choix=BREAKFAST");
        browser.post("/admin/inscriptions/nouvelle", "jour", WEEKEND.toString(), "benevole", formula.toString(), "choix",
                "BREAKFAST");
        browser.get("/admin/exports");

        var response = browser.post("/admin/exports/inscriptions.csv", "du", "2027-01-04", "au", "2027-01-10");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).startsWith("attachment")
                .contains("inscriptions-2027-01-04-2027-01-10.csv");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        var csv = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿\"Jour\";\"Activité\"")
                .contains("\"2027-01-05\";\"Dîner et nuit\";\"Passer la nuit et partager le dîner\";\"Martin\";\"Anne\";\""
                        + anne.pseudo() + "\";\"'+33600000001\";\"" + anne.email() + "\"")
                .contains("\"'=HYPERLINK(\"\"http://example.org\"\")\";\"'@Anne\"")
                // Hors période : le petit-déjeuner du 25 février.
                .doesNotContain("2027-02-25");
        assertThat(audits(coordinator.id(), "REGISTRATIONS_EXPORTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = 'REGISTRATIONS_EXPORTED'
                    AND period_from = DATE '2027-01-04' AND period_to = DATE '2027-01-10'
                """, Integer.class, coordinator.id())).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(anne.email()).doesNotContain("HYPERLINK");
    }


    @Test
    void the_page_shows_the_next_7_days_with_contact_details_without_audit(CapturedOutput output) throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        var coordinator = volunteers.coordinator();

        var response = coordinator.browser().get("/admin/exports");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString())
                .contains("value=\"2027-01-04\"").contains("value=\"2027-01-10\"")
                .contains("Ouvrir la page imprimable").contains("Exporter au format CSV").contains("Afficher")
                .contains("Mar. 5 janv.").contains("Dim. 10 janv.").doesNotContain("Lun. 11 janv.")
                .contains("Martin Anne (" + anne.pseudo() + ")").contains("+33600000001").contains(anne.email())
                .contains("hx-get=\"/admin/exports/tableau\"")
                .contains("src=\"/js/htmx-2.0.4.min.js\"").contains("src=\"/js/exports.js\"");
        assertThat(audits(coordinator.id(), "PRINTABLE_PLANNING_VIEWED")).isZero();
        assertThat(audits(coordinator.id(), "REGISTRATIONS_EXPORTED")).isZero();
        assertThat(output.getAll()).doesNotContain(anne.email());
    }

    @Test
    void the_page_shows_the_chosen_period_and_keeps_it_for_the_exports() throws Exception {
        var browser = volunteers.coordinator().browser();

        var html = browser.get("/admin/exports?du=2027-01-06&au=2027-01-08").getContentAsString();

        assertThat(html).contains("Mer. 6 janv.").contains("Ven. 8 janv.").doesNotContain("Mar. 5 janv.")
                .doesNotContain("Sam. 9 janv.")
                .contains("type=\"hidden\" name=\"du\" value=\"2027-01-06\"")
                .contains("type=\"hidden\" name=\"au\" value=\"2027-01-08\"");
    }

    @Test
    void the_fragment_contains_the_table_with_emails_and_the_export_buttons_for_the_period() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        var browser = volunteers.coordinator().browser();
        var pseudo = Browser.uniquePseudo();
        var noEmail = formulaVolunteer(pseudo);
        browser.get("/admin/inscriptions/nouvelle?jour=" + WEEKEND + "&benevole=" + noEmail + "&choix=BREAKFAST");
        browser.post("/admin/inscriptions/nouvelle", "jour", WEEKEND.toString(), "benevole", noEmail.toString(), "choix",
                "BREAKFAST");

        var response = browser.get("/admin/exports/tableau?du=2027-01-05&au=2027-01-09");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain("<html").doesNotContain("<header")
                .contains("Martin Anne (" + anne.pseudo() + ")").contains(anne.email())
                .contains("(" + pseudo + ")").contains("Sans e-mail")
                .contains("hx-swap-oob=\"true\"").contains("name=\"_csrf\"")
                .contains("type=\"hidden\" name=\"du\" value=\"2027-01-05\"")
                .contains("type=\"hidden\" name=\"au\" value=\"2027-01-09\"")
                .doesNotContain("disabled");
    }

    @Test
    void an_invalid_period_gives_a_clear_message_on_the_page_and_in_the_fragment() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        var browser = volunteers.coordinator().browser();
        var cases = new String[][] {
                {"du=2027-01-10&au=2027-01-04", "« Au » doit tomber le même jour que « Du » ou après."},
                {"du=2027-01-01&au=2027-03-01", "La période ne peut pas dépasser 31 jours."},
                {"du=hier&au=2027-01-04", "Indiquez deux dates valides."},
                {"du=&au=", "Indiquez deux dates valides."}};

        for (var c : cases) {
            for (var path : new String[] {"/admin/exports?", "/admin/exports/tableau?"}) {
                var response = browser.get(path + c[0]);

                assertThat(response.getStatus()).as(path + c[0]).isEqualTo(200);
                assertThat(response.getContentAsString()).as(path + c[0]).contains(c[1])
                        .doesNotContain(anne.email()).contains("disabled");
            }
        }
        // Les champs gardent ce qui a été saisi.
        assertThat(browser.get("/admin/exports?du=2027-01-10&au=2027-01-04").getContentAsString())
                .contains("value=\"2027-01-10\"").contains("value=\"2027-01-04\"");
    }

    @Test
    void an_invalid_period_is_refused_without_audit() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();

        for (var path : new String[] {"/admin/exports/inscriptions.csv", "/admin/exports/impression"}) {
            browser.get("/admin/exports");
            var inverted = browser.post(path, "du", "2027-01-10", "au", "2027-01-04");
            assertThat(inverted.getStatus()).as(path).isEqualTo(400);
            assertThat(inverted.getContentAsString()).as(path).contains("« Au » doit tomber le même jour");
            browser.get("/admin/exports");
            var tooLong = browser.post(path, "du", "2027-01-01", "au", "2027-02-01");
            assertThat(tooLong.getStatus()).as(path).isEqualTo(400);
            assertThat(tooLong.getContentAsString()).as(path).contains("31 jours");
            browser.get("/admin/exports");
            assertThat(browser.post(path, "du", "hier", "au", "2027-01-04").getStatus()).as(path).isEqualTo(400);
            browser.get("/admin/exports");
            assertThat(browser.post(path).getStatus()).as(path).isEqualTo(400);
        }
        assertThat(audits(coordinator.id(), "REGISTRATIONS_EXPORTED")).isZero();
        assertThat(audits(coordinator.id(), "PRINTABLE_PLANNING_VIEWED")).isZero();
    }

    @Test
    void the_csv_accepts_31_days() throws Exception {
        var browser = volunteers.coordinator().browser();
        browser.get("/admin/exports");

        assertThat(browser.post("/admin/exports/inscriptions.csv", "du", "2027-01-01", "au", "2027-01-31").getStatus())
                .isEqualTo(200);
    }

    @Test
    void exports_are_not_available_by_get() throws Exception {
        var browser = volunteers.coordinator().browser();

        assertThat(browser.get("/admin/exports/inscriptions.csv?du=2027-01-04&au=2027-01-10").getStatus())
                .isIn(403, 404, 405);
        assertThat(browser.get("/admin/exports/impression?du=2027-01-04&au=2027-01-10").getStatus())
                .isIn(403, 404, 405);
    }

    @Test
    void the_printable_planning_shows_the_chosen_period_with_contact_details() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/exports");

        var response = browser.post("/admin/exports/impression", "du", "2027-01-05", "au", "2027-01-06");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Planning du mar. 5 janv. au mer. 6 janv.")
                .contains("Mar. 5 janv.").contains("Mer. 6 janv.").doesNotContain("Jeu. 7 janv.")
                .doesNotContain("Lun. 4 janv.")
                .contains("Martin Anne (" + anne.pseudo() + ")").contains("+33600000001").contains(anne.email())
                .contains("place à pourvoir")
                .doesNotContain("htmx");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = 'PRINTABLE_PLANNING_VIEWED'
                    AND period_from = DATE '2027-01-05' AND period_to = DATE '2027-01-06'
                """, Integer.class, coordinator.id())).isEqualTo(1);
    }
}
