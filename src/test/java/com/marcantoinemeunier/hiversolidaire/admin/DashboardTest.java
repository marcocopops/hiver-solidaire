package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tableau de bord (cahier des charges §6) : journées à compléter des 14 prochains jours en tête, alertes non
 * traitées, préparations chez soi sans récupération (R4), nouveaux bénévoles (§2). Chaque action est auditée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class DashboardTest {

    /** Mardi 12 janvier 2027 : dans les 14 jours qui suivent l'horloge de test (lundi 4 janvier). */
    private static final LocalDate SOON = LocalDate.of(2027, 1, 12);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestVolunteers volunteers;

    @BeforeEach
    void setUp() {
        volunteers = new TestVolunteers(mockMvc, sender, coordinators, jdbc);
    }

    @AfterEach
    void clean() {
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                SOON);
        jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                SOON);
    }

    private int audits(UUID coordinatorId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = ?",
                Integer.class, coordinatorId, action);
    }

    @Test
    void incomplete_days_of_the_next_14_days_come_first_with_what_is_missing() throws Exception {
        var page = volunteers.coordinator().browser().get("/admin").getContentAsString();

        var incomplete = page.indexOf("Journées à compléter (14 prochains jours)");
        assertThat(incomplete).isPositive().isLessThan(page.indexOf("Alertes à traiter"));
        assertThat(page).contains("href=\"/admin/journee?jour=2027-01-04\"").contains("Lun. 4 janv.")
                .contains("href=\"/admin/journee?jour=2027-01-17\"")
                // Le 18 janvier est le 15e jour : hors de la fenêtre.
                .doesNotContain("href=\"/admin/journee?jour=2027-01-18\"")
                .contains("Nuit : 1 manquant").contains("Dîner : 2 manquants");
    }

    @Test
    void a_recorded_alert_is_shown_until_a_coordinator_handles_it() throws Exception {
        var alertId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO coordinator_alert (id, kind, evening_id, created_at)
                SELECT ?, 'EVENING_INCOMPLETE', id, now() FROM evening WHERE evening_date = ?
                """, alertId, SOON);
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();

        var page = browser.get("/admin").getContentAsString();
        assertThat(page).contains("Soirée du mar. 12 janv. devenue incomplète").contains(alertId.toString());

        var response = browser.post("/admin/alertes/traiter", "alerte", alertId.toString());

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin");
        assertThat(browser.get("/admin").getContentAsString()).doesNotContain(alertId.toString());
        assertThat(jdbc.queryForObject("SELECT handled_by FROM coordinator_alert WHERE id = ?", UUID.class, alertId))
                .isEqualTo(coordinator.id());
        assertThat(jdbc.queryForObject("SELECT sent_at IS NULL FROM coordinator_alert WHERE id = ?", Boolean.class,
                alertId)).as("handling an alert does not mark it as mailed").isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = 'ALERT_HANDLED' AND alert_id = ?
                """, Integer.class, coordinator.id(), alertId)).isEqualTo(1);

        // Une seconde fois : rien à faire, rien d'audité.
        browser.post("/admin/alertes/traiter", "alerte", alertId.toString());
        assertThat(audits(coordinator.id(), "ALERT_HANDLED")).isEqualTo(1);
    }

    @Test
    void a_home_preparation_without_pickup_is_listed() throws Exception {
        var volunteer = volunteers.volunteer();
        TestVolunteers.register(volunteer.browser(), SOON, "PREPARE_AT_HOME.MAIN_DISH");

        var page = volunteers.coordinator().browser().get("/admin").getContentAsString();

        var pickups = page.substring(page.indexOf("Repas préparés chez soi et récupérations"));
        assertThat(pickups).contains("Mar. 12 janv.").contains("1 repas à faire récupérer")
                .doesNotContain("sans repas");
    }

    @Test
    void a_pickup_left_without_meal_is_alerted_and_listed() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "PREPARE_AT_HOME.MAIN_DISH");
        var bob = volunteers.volunteer();
        TestVolunteers.register(bob.browser(), SOON, "PICK_UP_AND_SHARE");
        anne.browser().get("/mes-inscriptions/annuler?soiree=" + SOON + "&choix=PREPARE_AT_HOME.MAIN_DISH");
        anne.browser().post("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix",
                "PREPARE_AT_HOME.MAIN_DISH");

        var page = volunteers.coordinator().browser().get("/admin").getContentAsString();

        assertThat(page).contains("Soirée du mar. 12 janv. : une récupération n’a plus de repas à récupérer");
        var pickups = page.substring(page.indexOf("Repas préparés chez soi et récupérations"));
        assertThat(pickups).contains("Mar. 12 janv.").contains("1 récupération sans repas à récupérer")
                .doesNotContain("repas à faire récupérer");
    }

    @Test
    void a_new_volunteer_is_signalled_until_a_coordinator_has_seen_them() throws Exception {
        var volunteer = volunteers.volunteer();
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        assertThat(browser.get("/admin").getContentAsString()).contains("/admin/benevoles/" + volunteer.id())
                .contains(volunteer.pseudo());

        var response = browser.post("/admin/benevoles/vu", "benevole", volunteer.id().toString());

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin");
        assertThat(browser.get("/admin").getContentAsString()).doesNotContain("/admin/benevoles/" + volunteer.id());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE coordinator_id = ? AND action = 'VOLUNTEER_REVIEWED' AND volunteer_id = ?
                """, Integer.class, coordinator.id(), volunteer.id())).isEqualTo(1);
    }

    @Test
    void marking_an_unknown_volunteer_as_seen_is_not_found_and_not_audited() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin");

        assertThat(browser.post("/admin/benevoles/vu", "benevole", UUID.randomUUID().toString()).getStatus())
                .isEqualTo(404);
        assertThat(browser.post("/admin/benevoles/vu", "benevole", "not-a-uuid").getStatus()).isEqualTo(404);
        assertThat(audits(coordinator.id(), "VOLUNTEER_REVIEWED")).isZero();
    }

    @Test
    void the_dashboard_is_read_only() throws Exception {
        var browser = volunteers.coordinator().browser();
        var before = jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class);

        browser.get("/admin");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class)).isEqualTo(before);
    }
}
