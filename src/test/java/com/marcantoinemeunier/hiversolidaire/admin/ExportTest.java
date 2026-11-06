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

/** Exports (§6, §8) : CSV d'une période et page imprimable des 7 prochains jours, réservés et audités. */
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
        for (var date : new LocalDate[] {SOON, WEEKEND, LATER}) {
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
    void an_invalid_period_is_refused_without_audit() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/exports");

        assertThat(browser.post("/admin/exports/inscriptions.csv", "du", "2027-01-10", "au", "2027-01-04").getStatus())
                .isEqualTo(400);
        assertThat(browser.post("/admin/exports/inscriptions.csv", "du", "2027-01-01", "au", "2028-01-01").getStatus())
                .isEqualTo(400);
        assertThat(browser.post("/admin/exports/inscriptions.csv", "du", "hier", "au", "2027-01-04").getStatus())
                .isEqualTo(404);
        assertThat(audits(coordinator.id(), "REGISTRATIONS_EXPORTED")).isZero();
    }

    @Test
    void exports_are_not_available_by_get() throws Exception {
        var browser = volunteers.coordinator().browser();

        assertThat(browser.get("/admin/exports/inscriptions.csv?du=2027-01-04&au=2027-01-10").getStatus())
                .isIn(403, 404, 405);
        assertThat(browser.get("/admin/exports/impression").getStatus()).isIn(403, 404, 405);
    }

    @Test
    void the_printable_planning_shows_the_next_7_days_with_contact_details() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/exports");

        var response = browser.post("/admin/exports/impression");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Planning du lun. 4 janv. au dim. 10 janv.")
                .contains("Mar. 5 janv.").contains("Dim. 10 janv.").doesNotContain("Lun. 11 janv.")
                .contains("Martin Anne (" + anne.pseudo() + ") +33600000001")
                .contains("place à pourvoir");
        assertThat(audits(coordinator.id(), "PRINTABLE_PLANNING_VIEWED")).isEqualTo(1);
    }
}
