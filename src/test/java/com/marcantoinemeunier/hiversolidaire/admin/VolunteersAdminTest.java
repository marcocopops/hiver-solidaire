package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** Bénévoles du back-office (§6) : recherche, fiche, correction (§11 question 8), suppression sur demande (ADR-0008). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class VolunteersAdminTest {

    /** Jeudi 7 janvier 2027 : la limite de R6 tombe le 6 à 23 h 59. */
    private static final LocalDate SOON = LocalDate.of(2027, 1, 7);
    private static final LocalDate LATER = LocalDate.of(2027, 1, 27);

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
        for (var date : new LocalDate[] {SOON, LATER}) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    private int audits(UUID coordinatorId, String action, UUID volunteerId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = ? AND volunteer_id = ?",
                Integer.class, coordinatorId, action, volunteerId);
    }

    @Test
    void the_list_is_searchable_by_pseudo_and_email_with_like_characters_escaped() throws Exception {
        var anne = volunteers.volunteer();
        var bob = volunteers.volunteer();
        var browser = volunteers.coordinator().browser();

        assertThat(browser.get("/admin/benevoles").getContentAsString()).contains(anne.pseudo()).contains(bob.pseudo());
        assertThat(browser.get("/admin/benevoles?q=" + anne.pseudo().toLowerCase()).getContentAsString())
                .contains(anne.pseudo()).doesNotContain(bob.pseudo());
        assertThat(browser.get("/admin/benevoles?q=" + bob.email()).getContentAsString())
                .contains(bob.pseudo()).doesNotContain(anne.pseudo());
        assertThat(browser.get("/admin/benevoles?q=%25").getContentAsString())
                .doesNotContain(anne.pseudo()).contains("Aucun bénévole.");
        assertThat(browser.get("/admin/benevoles?q=_").getContentAsString()).doesNotContain(anne.pseudo());
    }

    @Test
    void the_sheet_shows_contact_details_and_registrations() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "BREAKFAST");
        var browser = volunteers.coordinator().browser();

        var page = browser.get("/admin/benevoles/" + anne.id()).getContentAsString();

        assertThat(page).contains(anne.email()).contains("+33600000001").contains(anne.pseudo())
                .contains("Petit-déjeuner du jeu. 28 janv.").contains("href=\"/admin/journee?jour=2027-01-28\"");
        assertThat(browser.get("/admin/benevoles/" + UUID.randomUUID()).getStatus()).isEqualTo(404);
        assertThat(browser.get("/admin/benevoles/not-a-uuid").getStatus()).isEqualTo(404);
    }

    @Test
    void a_coordinator_corrects_a_usurped_pseudo() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "NIGHT_AND_DINNER");
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/benevoles/" + anne.id());
        var fixed = Browser.uniquePseudo();

        var response = browser.post("/admin/benevoles/" + anne.id() + "/identite", "lastName", "Martin-Durand",
                "firstName", "Anne-Marie", "pseudo", fixed);

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/benevoles/" + anne.id());
        assertThat(jdbc.queryForMap("SELECT last_name, first_name, pseudo FROM volunteer WHERE id = ?", anne.id()))
                .containsEntry("last_name", "Martin-Durand").containsEntry("first_name", "Anne-Marie")
                .containsEntry("pseudo", fixed);
        assertThat(audits(coordinator.id(), "VOLUNTEER_IDENTITY_CORRECTED", anne.id())).isEqualTo(1);
        assertThat(new Browser(mockMvc).get("/planning").getContentAsString()).contains(fixed)
                .doesNotContain(anne.pseudo());
    }

    @Test
    void a_correction_follows_the_same_rules_as_the_profile() throws Exception {
        var anne = volunteers.volunteer();
        var bob = volunteers.volunteer();
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/benevoles/" + anne.id());

        var taken = browser.post("/admin/benevoles/" + anne.id() + "/identite", "lastName", "Martin", "firstName",
                "Anne", "pseudo", bob.pseudo().toUpperCase());
        assertThat(taken.getContentAsString()).contains("Ce pseudo est déjà pris");

        var invalid = browser.post("/admin/benevoles/" + anne.id() + "/identite", "lastName", "<b>", "firstName",
                "Anne", "pseudo", "Coordination<");
        assertThat(invalid.getContentAsString()).contains("Indiquez votre nom").contains("Le pseudo doit faire")
                .doesNotContain("<b>");

        assertThat(jdbc.queryForObject("SELECT pseudo FROM volunteer WHERE id = ?", String.class, anne.id()))
                .isEqualTo(anne.pseudo());
        assertThat(audits(coordinator.id(), "VOLUNTEER_IDENTITY_CORRECTED", anne.id())).isZero();
    }

    @Test
    void a_coordinator_deletes_a_volunteer_on_request_with_the_adr_0008_anonymisation() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        TestVolunteers.register(anne.browser(), LATER, "BREAKFAST");
        clock.set(LocalDateTime.of(2027, 1, 7, 9, 0).atZone(ParisTime.ZONE).toInstant());
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();

        var confirm = browser.get("/admin/benevoles/" + anne.id() + "/supprimer");
        assertThat(confirm.getContentAsString()).contains("2 inscriptions à venir seront annulées.");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM volunteer WHERE id = ?", Boolean.class,
                anne.id())).isTrue();

        var response = browser.post("/admin/benevoles/" + anne.id() + "/supprimer");

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/benevoles");
        assertThat(jdbc.queryForMap("""
                SELECT email, last_name, first_name, phone, pseudo, deleted_at IS NOT NULL AS deleted
                FROM volunteer WHERE id = ?
                """, anne.id())).containsEntry("email", null).containsEntry("last_name", null)
                .containsEntry("pseudo", null).containsEntry("deleted", true);
        // Inscriptions à venir annulées, y compris celle du jour même (après la limite de R6).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE volunteer_id = ? AND cancelled_at IS NULL",
                Integer.class, anne.id())).isZero();
        // Déconnecté de tous ses appareils.
        assertThat(anne.browser().get("/mes-inscriptions").getRedirectedUrl()).endsWith("/connexion");
        assertThat(audits(coordinator.id(), "VOLUNTEER_DELETED", anne.id())).isEqualTo(1);
        assertThat(browser.get("/admin/benevoles/" + anne.id()).getStatus()).isEqualTo(404);
    }

    @Test
    void a_coordinator_cannot_delete_their_own_account_from_the_back_office() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();

        assertThat(browser.get("/admin/benevoles/" + coordinator.id() + "/supprimer").getContentAsString())
                .contains("passez par « Mon compte »").doesNotContain("Supprimer définitivement");
        assertThat(browser.post("/admin/benevoles/" + coordinator.id() + "/supprimer").getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM volunteer WHERE id = ?", Boolean.class,
                coordinator.id())).isTrue();
    }
}
