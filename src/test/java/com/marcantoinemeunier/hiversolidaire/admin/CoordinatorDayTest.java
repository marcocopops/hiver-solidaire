package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers.SignedIn;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Fiche journée et actions des coordinateurs (cahier des charges §6 « Fiche soirée ») : coordonnées des inscrits,
 * annulation même après R6, déplacement sous R1 à R4 et R7, inscription d'un bénévole existant ou sans e-mail. Chaque
 * action passe par une page de confirmation (GET sans effet), puis un POST, et écrit une ligne d'audit. Les règles du
 * lot 3 (un poste par bénévole et par soirée, récupération liée aux préparations chez soi) s'y appliquent comme en
 * ligne.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class CoordinatorDayTest {

    /** Mercredi 6 janvier 2027 : dans la fenêtre de l'alerte ; limite de R6 le mardi 5 à 23 h 59. */
    private static final LocalDate SOON = LocalDate.of(2027, 1, 6);
    /** Mercredi 20 et jeudi 21 janvier 2027. */
    private static final LocalDate LATER = LocalDate.of(2027, 1, 20);
    private static final LocalDate LATER_NEXT = LATER.plusDays(1);
    private static final LocalDate[] DATES = {SOON, LATER, LATER_NEXT, LATER.minusDays(1)};

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
        for (var date : DATES) {
            jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
            jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                    date);
        }
    }

    private UUID activeRegistration(UUID volunteerId, LocalDate eveningDate) {
        return jdbc.queryForObject("""
                SELECT r.id FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.volunteer_id = ? AND e.evening_date = ? AND r.cancelled_at IS NULL
                """, UUID.class, volunteerId, eveningDate);
    }

    private int active(UUID volunteerId, LocalDate eveningDate) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.volunteer_id = ? AND e.evening_date = ? AND r.cancelled_at IS NULL
                """, Integer.class, volunteerId, eveningDate);
    }

    private int audits(UUID coordinatorId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = ?",
                Integer.class, coordinatorId, action);
    }

    private static void at(MutableClock clock, LocalDate date, int hour) {
        clock.set(LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)).atZone(ParisTime.ZONE).toInstant());
    }

    @Test
    void the_day_sheet_shows_both_evenings_with_contact_details() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "NIGHT_AND_DINNER");
        var bob = volunteers.volunteer();
        TestVolunteers.register(bob.browser(), LATER.minusDays(1), "BREAKFAST");

        var page = volunteers.coordinator().browser().get("/admin/journee?jour=" + LATER);

        assertThat(page.getStatus()).isEqualTo(200);
        var html = page.getContentAsString();
        assertThat(html).contains("Journée du Mer. 20 janv.")
                .contains("Petit-déjeuner (soirée du mar. 19 janv.)")
                .contains("Dîner et nuit (soirée du mer. 20 janv.)")
                .contains(anne.pseudo()).contains(anne.email()).contains("+33600000001").contains("Martin Anne")
                .contains(bob.pseudo()).contains(bob.email())
                .contains("/admin/inscriptions/annuler?inscription=" + activeRegistration(anne.id(), LATER))
                .contains("/admin/inscriptions/deplacer?inscription=" + activeRegistration(bob.id(), LATER.minusDays(1)));
        // Le planning public, lui, ne montre que les pseudos.
        var publicPage = new Browser(mockMvc).get("/planning").getContentAsString();
        assertThat(publicPage).contains(anne.pseudo()).doesNotContain(anne.email()).doesNotContain("+33600000001")
                .doesNotContain("Martin");
    }

    @Test
    void an_unknown_or_malformed_day_is_not_found() throws Exception {
        var browser = volunteers.coordinator().browser();

        assertThat(browser.get("/admin/journee?jour=2030-01-01").getStatus()).isEqualTo(404);
        assertThat(browser.get("/admin/journee?jour=hier").getStatus()).isEqualTo(404);
        assertThat(browser.get("/admin/inscriptions/annuler?inscription=" + UUID.randomUUID()).getStatus())
                .isEqualTo(404);
    }

    @Test
    void a_coordinator_cancels_after_the_r6_deadline_when_the_volunteer_no_longer_can() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "NIGHT_AND_DINNER");
        var registration = activeRegistration(anne.id(), SOON);
        at(clock, SOON, 12);
        // Connecté après le changement d'horloge : une session de coordinateur ne dure que 8 h.
        var coordinator = volunteers.coordinator();

        // Le bénévole ne peut plus se désinscrire seul.
        anne.browser().get("/mes-inscriptions/annuler?soiree=" + SOON + "&choix=NIGHT_AND_DINNER");
        assertThat(anne.browser().post("/mes-inscriptions/annuler", "soiree", SOON.toString(), "choix",
                "NIGHT_AND_DINNER").getRedirectedUrl())
                .isEqualTo("/mes-inscriptions/annuler?soiree=" + SOON + "&choix=NIGHT_AND_DINNER");
        assertThat(active(anne.id(), SOON)).isEqualTo(1);

        // Le coordinateur, si : la page de confirmation n'annule rien.
        var confirm = coordinator.browser().get("/admin/inscriptions/annuler?inscription=" + registration);
        assertThat(confirm.getContentAsString()).contains("Dîner et nuit du mer. 6 janv.")
                .contains("La limite de désinscription en ligne est passée")
                .contains("name=\"inscription\" value=\"" + registration + "\"");
        assertThat(active(anne.id(), SOON)).isEqualTo(1);

        var response = coordinator.browser().post("/admin/inscriptions/annuler", "inscription", registration.toString());

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/journee?jour=" + SOON);
        assertThat(active(anne.id(), SOON)).isZero();
        assertThat(jdbc.queryForObject("SELECT cancelled_at IS NOT NULL FROM registration WHERE id = ?", Boolean.class,
                registration)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = 'REGISTRATION_CANCELLED'
                    AND registration_id = ? AND volunteer_id = ?
                    AND evening_id = (SELECT id FROM evening WHERE evening_date = ?)
                """, Integer.class, coordinator.id(), registration, anne.id(), SOON)).isEqualTo(1);
        // La soirée perd son seul bénévole de nuit : l'alerte est enregistrée comme pour un bénévole.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id WHERE e.evening_date = ?
                """, Integer.class, SOON)).isEqualTo(1);

        // Une seconde annulation ne trouve plus rien.
        assertThat(coordinator.browser().post("/admin/inscriptions/annuler", "inscription", registration.toString())
                .getStatus()).isEqualTo(404);
        assertThat(audits(coordinator.id(), "REGISTRATION_CANCELLED")).isEqualTo(1);
    }

    @Test
    void a_past_evening_cannot_be_changed() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), SOON, "BREAKFAST");
        var registration = activeRegistration(anne.id(), SOON);
        at(clock, SOON.plusDays(1), 12);
        var coordinator = volunteers.coordinator();

        var confirm = coordinator.browser().get("/admin/inscriptions/annuler?inscription=" + registration);
        assertThat(confirm.getStatus()).isEqualTo(200);
        coordinator.browser().post("/admin/inscriptions/annuler", "inscription", registration.toString());

        assertThat(active(anne.id(), SOON)).isEqualTo(1);
        assertThat(audits(coordinator.id(), "REGISTRATION_CANCELLED")).isZero();
    }

    @Test
    void a_coordinator_moves_a_registration_to_another_evening() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "PREPARE_AND_SHARE.MAIN_DISH");
        var registration = activeRegistration(anne.id(), LATER);
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();

        // Choix de la journée, puis confirmation : deux GET sans effet.
        assertThat(browser.get("/admin/inscriptions/deplacer?inscription=" + registration).getContentAsString())
                .contains("type=\"date\" name=\"jour\"");
        var confirm = browser.get("/admin/inscriptions/deplacer?inscription=" + registration + "&jour=" + LATER_NEXT);
        assertThat(confirm.getContentAsString()).contains("Déplacer vers : Dîner du jeu. 21 janv.");
        assertThat(active(anne.id(), LATER)).isEqualTo(1);

        var response = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(), "jour",
                LATER_NEXT.toString());

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/journee?jour=" + LATER_NEXT);
        assertThat(active(anne.id(), LATER)).isZero();
        assertThat(active(anne.id(), LATER_NEXT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT r.type || '.' || r.dish_choice FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.volunteer_id = ? AND e.evening_date = ? AND r.cancelled_at IS NULL
                """, String.class, anne.id(), LATER_NEXT)).isEqualTo("PREPARE_AND_SHARE.MAIN_DISH");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = 'REGISTRATION_MOVED'
                    AND registration_id = ? AND volunteer_id = ?
                    AND evening_id = (SELECT id FROM evening WHERE evening_date = ?)
                    AND target_evening_id = (SELECT id FROM evening WHERE evening_date = ?)
                """, Integer.class, coordinator.id(), registration, anne.id(), LATER, LATER_NEXT)).isEqualTo(1);
    }

    @Test
    void a_move_follows_r1_to_r4_and_r7_on_the_target_evening() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "PREPARE_AND_SHARE.MAIN_DISH");
        var registration = activeRegistration(anne.id(), LATER);
        // R3 : le plat principal du lendemain est déjà pris.
        var bob = volunteers.volunteer();
        TestVolunteers.register(bob.browser(), LATER_NEXT, "PREPARE_AT_HOME.MAIN_DISH");
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/inscriptions/deplacer?inscription=" + registration + "&jour=" + LATER_NEXT);

        var dishTaken = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(), "jour",
                LATER_NEXT.toString());

        assertThat(dishTaken.getRedirectedUrl()).endsWith("&refus=DISH_TAKEN");
        assertThat(browser.get(dishTaken.getRedirectedUrl()).getContentAsString())
                .contains("Ce plat est déjà préparé par quelqu’un d’autre.");
        assertThat(active(anne.id(), LATER)).isEqualTo(1);

        // R2 : Anne prépare déjà le plat principal la veille (chez elle) : même poste.
        TestVolunteers.register(anne.browser(), LATER.minusDays(1), "PREPARE_AT_HOME.MAIN_DISH");
        var alreadyRegistered = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(),
                "jour", LATER.minusDays(1).toString());
        assertThat(alreadyRegistered.getRedirectedUrl()).endsWith("&refus=ALREADY_REGISTERED");

        // R7 : une soirée passée.
        var past = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(), "jour",
                "2027-01-03");
        assertThat(past.getStatus()).isEqualTo(404);

        // Même soirée.
        assertThat(browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(), "jour",
                LATER.toString()).getRedirectedUrl()).endsWith("&refus=SAME_EVENING");

        // R7 : la soirée du 4 janvier est passée le 5.
        at(clock, LocalDate.of(2027, 1, 5), 10);
        var later = volunteers.coordinator();
        later.browser().get("/admin/inscriptions/deplacer?inscription=" + registration);
        var pastInSeason = later.browser().post("/admin/inscriptions/deplacer", "inscription", registration.toString(),
                "jour", "2027-01-04");
        assertThat(pastInSeason.getRedirectedUrl()).endsWith("&refus=PAST");

        assertThat(active(anne.id(), LATER)).isEqualTo(1);
        assertThat(audits(coordinator.id(), "REGISTRATION_MOVED")).isZero();
        assertThat(audits(later.id(), "REGISTRATION_MOVED")).isZero();
    }

    @Test
    void a_move_to_a_full_post_is_refused_by_r1() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "NIGHT_AND_DINNER");
        var registration = activeRegistration(anne.id(), LATER);
        for (int i = 0; i < 2; i++) {
            TestVolunteers.register(volunteers.volunteer().browser(), LATER_NEXT, "NIGHT_AND_DINNER");
        }
        var browser = volunteers.coordinator().browser();
        browser.get("/admin/inscriptions/deplacer?inscription=" + registration + "&jour=" + LATER_NEXT);

        var response = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(), "jour",
                LATER_NEXT.toString());

        assertThat(response.getRedirectedUrl()).endsWith("&refus=FULL");
        assertThat(active(anne.id(), LATER)).isEqualTo(1);
    }

    @Test
    void a_coordinator_registers_an_existing_volunteer_with_the_same_rules() throws Exception {
        var anne = volunteers.volunteer();
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();

        assertThat(browser.get("/admin/inscriptions/nouvelle?jour=" + LATER).getContentAsString())
                .contains("value=\"" + anne.id() + "\"");
        assertThat(browser.get("/admin/inscriptions/nouvelle?jour=" + LATER + "&benevole=" + anne.id())
                .getContentAsString()).contains("value=\"NIGHT_AND_DINNER\"").contains("value=\"BREAKFAST\"");
        var confirm = browser.get("/admin/inscriptions/nouvelle?jour=" + LATER + "&benevole=" + anne.id()
                + "&choix=NIGHT_AND_DINNER");
        assertThat(confirm.getContentAsString()).contains("Dîner et nuit du mer. 20 janv.").contains(anne.pseudo());
        assertThat(active(anne.id(), LATER)).isZero();

        var response = browser.post("/admin/inscriptions/nouvelle", "jour", LATER.toString(), "benevole",
                anne.id().toString(), "choix", "NIGHT_AND_DINNER");

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/journee?jour=" + LATER);
        assertThat(active(anne.id(), LATER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = 'REGISTRATION_CREATED'
                    AND registration_id = ? AND volunteer_id = ?
                """, Integer.class, coordinator.id(), activeRegistration(anne.id(), LATER), anne.id())).isEqualTo(1);

        // R2 : le même poste une seconde fois est refusé, sans audit.
        var again = browser.post("/admin/inscriptions/nouvelle", "jour", LATER.toString(), "benevole",
                anne.id().toString(), "choix", "NIGHT_AND_DINNER");
        assertThat(again.getRedirectedUrl()).endsWith("&refus=ALREADY_REGISTERED");
        assertThat(browser.get(again.getRedirectedUrl()).getContentAsString())
                .contains("Ce bénévole a déjà ce poste sur cette soirée.");
        assertThat(audits(coordinator.id(), "REGISTRATION_CREATED")).isEqualTo(1);

        // R2 : un autre poste de la même soirée se cumule.
        assertThat(browser.get("/admin/inscriptions/nouvelle?jour=" + LATER + "&benevole=" + anne.id())
                .getContentAsString()).contains("value=\"PREPARE_AND_SHARE.MAIN_DISH\"")
                .doesNotContain("value=\"NIGHT_AND_DINNER\"");
        var cumulated = browser.post("/admin/inscriptions/nouvelle", "jour", LATER.toString(), "benevole",
                anne.id().toString(), "choix", "PREPARE_AND_SHARE.MAIN_DISH");
        assertThat(cumulated.getRedirectedUrl()).isEqualTo("/admin/journee?jour=" + LATER);
        assertThat(active(anne.id(), LATER)).isEqualTo(2);
        assertThat(audits(coordinator.id(), "REGISTRATION_CREATED")).isEqualTo(2);
    }

    private int pickupWithoutMealAlerts(LocalDate eveningDate) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id
                WHERE e.evening_date = ? AND a.kind = 'PICKUP_WITHOUT_MEAL'
                """, Integer.class, eveningDate);
    }

    private UUID activeRegistration(UUID volunteerId, LocalDate eveningDate, String type) {
        return jdbc.queryForObject("""
                SELECT r.id FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.volunteer_id = ? AND e.evening_date = ? AND r.type = ? AND r.cancelled_at IS NULL
                """, UUID.class, volunteerId, eveningDate, type);
    }

    @Test
    void a_phone_registration_offers_and_accepts_a_pickup_only_for_a_meal_prepared_at_home() throws Exception {
        var anne = volunteers.volunteer();
        var carol = volunteers.volunteer();
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        var choices = "/admin/inscriptions/nouvelle?jour=" + LATER + "&benevole=" + anne.id();
        assertThat(browser.get(choices).getContentAsString()).doesNotContain("value=\"PICK_UP_AND_SHARE\"");

        TestVolunteers.register(volunteers.volunteer().browser(), LATER, "PREPARE_AT_HOME.MAIN_DISH");
        assertThat(browser.get(choices).getContentAsString()).contains("value=\"PICK_UP_AND_SHARE\"");
        var accepted = browser.post("/admin/inscriptions/nouvelle", "jour", LATER.toString(), "benevole",
                anne.id().toString(), "choix", "PICK_UP_AND_SHARE");
        assertThat(accepted.getRedirectedUrl()).isEqualTo("/admin/journee?jour=" + LATER);

        // Le repas a sa récupération : une deuxième est refusée, sans audit.
        browser.get("/admin/inscriptions/nouvelle?jour=" + LATER + "&benevole=" + carol.id());
        var refused = browser.post("/admin/inscriptions/nouvelle", "jour", LATER.toString(), "benevole",
                carol.id().toString(), "choix", "PICK_UP_AND_SHARE");
        assertThat(refused.getRedirectedUrl()).endsWith("&refus=NOTHING_TO_PICK_UP");
        assertThat(browser.get(refused.getRedirectedUrl()).getContentAsString())
                .contains("Aucun repas préparé chez soi n’attend de récupération sur cette soirée.");
        assertThat(active(carol.id(), LATER)).isZero();
        assertThat(audits(coordinator.id(), "REGISTRATION_CREATED")).isEqualTo(1);
    }

    @Test
    void a_move_of_a_pickup_needs_a_meal_and_no_other_dinner_post_on_the_target_evening() throws Exception {
        TestVolunteers.register(volunteers.volunteer().browser(), LATER, "PREPARE_AT_HOME.MAIN_DISH");
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "PICK_UP_AND_SHARE");
        var registration = activeRegistration(anne.id(), LATER);
        var browser = volunteers.coordinator().browser();
        browser.get("/admin/inscriptions/deplacer?inscription=" + registration + "&jour=" + LATER_NEXT);

        // R4 : aucun repas préparé chez soi le lendemain.
        var nothing = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(), "jour",
                LATER_NEXT.toString());
        assertThat(nothing.getRedirectedUrl()).endsWith("&refus=NOTHING_TO_PICK_UP");

        // R2 : un repas attend, mais Anne passe déjà la nuit ce soir-là.
        TestVolunteers.register(volunteers.volunteer().browser(), LATER_NEXT, "PREPARE_AT_HOME.SIDES");
        TestVolunteers.register(anne.browser(), LATER_NEXT, "NIGHT_AND_DINNER");
        var notCumulable = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(),
                "jour", LATER_NEXT.toString());
        assertThat(notCumulable.getRedirectedUrl()).endsWith("&refus=NOT_CUMULABLE");
        assertThat(browser.get(notCumulable.getRedirectedUrl()).getContentAsString())
                .contains("« Récupérer le dîner et le partager » ne se cumule qu’avec le petit-déjeuner.");
        assertThat(active(anne.id(), LATER)).isEqualTo(1);
    }

    @Test
    void a_move_to_an_evening_where_the_volunteer_holds_another_post_is_accepted() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "PREPARE_AND_SHARE.SIDES");
        TestVolunteers.register(anne.browser(), LATER_NEXT, "NIGHT_AND_DINNER");
        var registration = activeRegistration(anne.id(), LATER);
        var browser = volunteers.coordinator().browser();
        browser.get("/admin/inscriptions/deplacer?inscription=" + registration + "&jour=" + LATER_NEXT);

        var response = browser.post("/admin/inscriptions/deplacer", "inscription", registration.toString(), "jour",
                LATER_NEXT.toString());

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/journee?jour=" + LATER_NEXT);
        assertThat(active(anne.id(), LATER)).isZero();
        assertThat(active(anne.id(), LATER_NEXT)).isEqualTo(2);
    }

    @Test
    void moving_or_cancelling_a_home_preparation_already_picked_up_alerts_the_coordinators() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "PREPARE_AT_HOME.MAIN_DISH");
        TestVolunteers.register(volunteers.volunteer().browser(), LATER, "PICK_UP_AND_SHARE");
        var bob = volunteers.volunteer();
        TestVolunteers.register(bob.browser(), LATER_NEXT, "PREPARE_AT_HOME.SIDES");
        TestVolunteers.register(volunteers.volunteer().browser(), LATER_NEXT, "PICK_UP_AND_SHARE");
        var browser = volunteers.coordinator().browser();

        // Déplacement : la soirée quittée garde sa récupération, sans repas.
        var moved = activeRegistration(anne.id(), LATER, "PREPARE_AT_HOME");
        browser.get("/admin/inscriptions/deplacer?inscription=" + moved + "&jour=" + LATER.minusDays(1));
        assertThat(browser.post("/admin/inscriptions/deplacer", "inscription", moved.toString(), "jour",
                LATER.minusDays(1).toString()).getRedirectedUrl()).isEqualTo("/admin/journee?jour=" + LATER.minusDays(1));
        assertThat(pickupWithoutMealAlerts(LATER)).isEqualTo(1);

        // Annulation par un coordinateur : même alerte.
        var cancelled = activeRegistration(bob.id(), LATER_NEXT, "PREPARE_AT_HOME");
        browser.get("/admin/inscriptions/annuler?inscription=" + cancelled);
        browser.post("/admin/inscriptions/annuler", "inscription", cancelled.toString());
        assertThat(pickupWithoutMealAlerts(LATER_NEXT)).isEqualTo(1);

        assertThat(browser.get("/admin/journee?jour=" + LATER).getContentAsString())
                .contains("Une récupération n’a plus de repas à récupérer : prévenez la personne inscrite.")
                .doesNotContain("Un repas préparé chez soi n’a pas de récupération");
    }

    @Test
    void the_day_sheet_flags_a_volunteer_counting_for_two_dinner_presences() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "PREPARE_AND_SHARE.MAIN_DISH");
        TestVolunteers.register(anne.browser(), LATER, "PREPARE_AND_SHARE.SIDES");
        var bob = volunteers.volunteer();
        TestVolunteers.register(bob.browser(), LATER_NEXT, "PREPARE_AND_SHARE.MAIN_DISH");

        var browser = volunteers.coordinator().browser();
        var sheet = browser.get("/admin/journee?jour=" + LATER).getContentAsString();

        assertThat(sheet).contains(anne.pseudo() + " compte pour 2 présences au dîner : vérifiez qu’ils viennent à deux.");
        assertThat(browser.get("/admin/journee?jour=" + LATER_NEXT).getContentAsString())
                .doesNotContain("compte pour 2 présences");
    }

    @Test
    void a_volunteer_without_email_is_created_registered_and_shown_by_pseudo(CapturedOutput output) throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/benevoles/nouveau?jour=" + LATER);
        var pseudo = Browser.uniquePseudo();

        var created = browser.post("/admin/benevoles/nouveau", "jour", LATER.toString(), "lastName", "Zébulon",
                "firstName", "Philomène", "pseudo", pseudo, "phoneCountryCode", "+33", "phone", "06 00 00 00 77");

        var id = jdbc.queryForObject("SELECT id FROM volunteer WHERE pseudo = ?", UUID.class, pseudo);
        assertThat(created.getRedirectedUrl())
                .isEqualTo("/admin/inscriptions/nouvelle?jour=" + LATER + "&benevole=" + id);
        assertThat(jdbc.queryForMap("SELECT email, phone, profile_completed_at, reviewed_at FROM volunteer WHERE id = ?",
                id)).containsEntry("email", null).containsEntry("phone", "+33600000077")
                .hasEntrySatisfying("profile_completed_at", value -> assertThat(value).isNotNull())
                .hasEntrySatisfying("reviewed_at", value -> assertThat(value).isNotNull());
        assertThat(audits(coordinator.id(), "VOLUNTEER_CREATED")).isEqualTo(1);

        browser.get(created.getRedirectedUrl() + "&choix=BREAKFAST");
        browser.post("/admin/inscriptions/nouvelle", "jour", LATER.toString(), "benevole", id.toString(), "choix",
                "BREAKFAST");

        assertThat(active(id, LATER.minusDays(1))).isEqualTo(1);
        var publicPage = new Browser(mockMvc).get("/planning").getContentAsString();
        assertThat(publicPage).contains(pseudo).doesNotContain("Zébulon").doesNotContain("Philomène");
        assertThat(browser.get("/admin/journee?jour=" + LATER).getContentAsString()).contains("Zébulon Philomène")
                .contains("Sans e-mail");
        // Aucune donnée personnelle dans les logs.
        assertThat(output.getAll()).doesNotContain("Zébulon").doesNotContain("Philomène")
                .doesNotContain("600000077").doesNotContain(coordinator.email());
    }

    @Test
    void a_volunteer_without_email_cannot_sign_in() throws Exception {
        var browser = volunteers.coordinator().browser();
        browser.get("/admin/benevoles/nouveau");
        var pseudo = Browser.uniquePseudo();
        browser.post("/admin/benevoles/nouveau", "lastName", "Martin", "firstName", "Anne", "pseudo", pseudo,
                "phoneCountryCode", "+33", "phone", "0600000002");
        var id = jdbc.queryForObject("SELECT id FROM volunteer WHERE pseudo = ?", UUID.class, pseudo);

        // Aucune adresse ne mène à ce compte : une connexion crée toujours un autre compte.
        var other = volunteers.signIn(Browser.uniqueEmail(), false);

        assertThat(other.id()).isNotEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM one_time_token WHERE username IS NULL", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT email IS NULL FROM volunteer WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void a_new_volunteer_needs_valid_fields_and_a_free_pseudo() throws Exception {
        var existing = volunteers.volunteer();
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        browser.get("/admin/benevoles/nouveau");

        var invalid = browser.post("/admin/benevoles/nouveau", "lastName", "<script>", "firstName", "Anne", "pseudo",
                "ab", "phoneCountryCode", "+33", "phone", "12");
        assertThat(invalid.getStatus()).isEqualTo(200);
        assertThat(invalid.getContentAsString()).contains("Indiquez votre nom").doesNotContain("<script>");

        var taken = browser.post("/admin/benevoles/nouveau", "lastName", "Martin", "firstName", "Anne", "pseudo",
                existing.pseudo(), "phoneCountryCode", "+33", "phone", "0600000003");
        assertThat(taken.getContentAsString()).contains("Ce pseudo est déjà pris");
        assertThat(audits(coordinator.id(), "VOLUNTEER_CREATED")).isZero();
    }

    @Test
    void confirmation_pages_change_nothing() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LATER, "NIGHT_AND_DINNER");
        var registration = activeRegistration(anne.id(), LATER);
        var browser = volunteers.coordinator().browser();
        var before = jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class);

        browser.get("/admin/journee?jour=" + LATER);
        browser.get("/admin/inscriptions/annuler?inscription=" + registration);
        browser.get("/admin/inscriptions/deplacer?inscription=" + registration + "&jour=" + LATER_NEXT);
        browser.get("/admin/inscriptions/nouvelle?jour=" + LATER_NEXT + "&benevole=" + anne.id() + "&choix=BREAKFAST");
        browser.get("/admin/benevoles/nouveau?jour=" + LATER);

        assertThat(active(anne.id(), LATER)).isEqualTo(1);
        assertThat(active(anne.id(), LATER_NEXT)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class)).isEqualTo(before);
    }
}
