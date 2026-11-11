package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Saison (cahier des charges §6 « Saison », ADR-0006) : création qui remplace la saison d'exemple, génération des
 * soirées, refus d'un changement de dates qui supprimerait des soirées avec inscriptions.
 *
 * <p>Ces tests changent la saison vue par toute l'appli : ils ont leur propre contexte Spring (propriété distincte),
 * donc leur propre base Testcontainers, recréée après chaque test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
@TestPropertySource(properties = "test.isolated-database=season")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SeasonAdminTest {

    private static final LocalDate FIRST = LocalDate.of(2027, 1, 4);
    private static final LocalDate LAST = LocalDate.of(2027, 3, 28);

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
    void resetClock() {
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
    }

    /** Champs du formulaire de saison, besoins de 2025-2026 dans l'ordre d'une journée. */
    private static String[] form(LocalDate first, LocalDate last, String... overrides) {
        var params = new ArrayList<>(List.of(
                "firstEvening", first.toString(),
                "lastEvening", last.toString(),
                "registrationsOpenAt", "2026-12-14T00:00",
                "coordinatorsContact", "",
                "organizer", ""));
        String[][] needs = {{"BREAKFAST", "1", "1"}, {"MAIN_DISH", "1", "1"}, {"SIDES", "1", "1"},
            {"DINNER_PRESENCE", "2", "2"}, {"NIGHT", "1", "2"}};
        for (int i = 0; i < needs.length; i++) {
            params.addAll(List.of("needs[" + i + "].post", needs[i][0], "needs[" + i + "].minimum", needs[i][1],
                    "needs[" + i + "].maximum", needs[i][2]));
        }
        for (int i = 0; i < overrides.length; i += 2) {
            var index = params.indexOf(overrides[i]);
            if (index >= 0) {
                params.set(index + 1, overrides[i + 1]);
            } else {
                params.addAll(List.of(overrides[i], overrides[i + 1]));
            }
        }
        return params.toArray(String[]::new);
    }

    private MockHttpServletResponse create(Browser browser, String... overrides) throws Exception {
        browser.get("/admin/saison/nouvelle");
        return browser.post("/admin/saison/nouvelle", form(FIRST, LAST, overrides));
    }

    private int evenings() {
        return jdbc.queryForObject("SELECT count(*) FROM evening", Integer.class);
    }

    private int audits(UUID coordinatorId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE coordinator_id = ? AND action = ?",
                Integer.class, coordinatorId, action);
    }

    @Test
    void a_new_season_replaces_the_sample_season_and_generates_its_evenings() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        assertThat(browser.get("/admin/saison").getRedirectedUrl()).isEqualTo("/admin/saison/nouvelle");
        assertThat(browser.get("/admin/saison/nouvelle").getContentAsString()).contains("saison d’exemple");

        var response = create(browser);

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/saison");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM season", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sample FROM season", Boolean.class)).isFalse();
        // ADR-0006 : une soirée par date, bornes comprises (4 janvier → 28 mars : 84 soirées).
        assertThat(evenings()).isEqualTo(84);
        assertThat(jdbc.queryForObject("SELECT min(evening_date) FROM evening", LocalDate.class)).isEqualTo(FIRST);
        assertThat(jdbc.queryForObject("SELECT max(evening_date) FROM evening", LocalDate.class)).isEqualTo(LAST);
        assertThat(jdbc.queryForObject("SELECT maximum FROM season_need WHERE post = 'NIGHT'", Integer.class))
                .isEqualTo(2);
        assertThat(audits(coordinator.id(), "SAMPLE_SEASON_DELETED")).isEqualTo(1);
        assertThat(audits(coordinator.id(), "SEASON_CREATED")).isEqualTo(1);
        assertThat(browser.get("/admin/saison").getContentAsString()).contains("value=\"2027-01-04\"")
                .doesNotContain("Nom de la saison").doesNotContain("Adresse du lieu").doesNotContain("Consignes")
                .doesNotContain("Horaires").doesNotContain(".schedule");

        // Une seule saison active à la fois : une seconde création est refusée.
        var again = create(browser, "lastEvening", LAST.plusDays(1).toString());
        assertThat(again.getContentAsString()).contains("Une saison est déjà en cours ou à venir");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM season", Integer.class)).isEqualTo(1);
    }

    @Test
    void the_organizer_is_saved_and_shown_on_the_home_page_with_the_season_dates() throws Exception {
        var browser = volunteers.coordinator().browser();
        create(browser, "organizer", "  Paroisse Saint-Exemple  ", "coordinatorsContact", "coordination@example.org");

        assertThat(jdbc.queryForObject("SELECT organizer FROM season", String.class))
                .isEqualTo("Paroisse Saint-Exemple");
        assertThat(browser.get("/admin/saison").getContentAsString()).contains("Organisateur")
                .contains("value=\"Paroisse Saint-Exemple\"");
        var home = new Browser(mockMvc).get("/").getContentAsString();
        assertThat(home).contains("Site d’inscription à Hiver Solidaire Paroisse Saint-Exemple")
                .contains("Hiver Solidaire accueillera du lundi 4 janvier 2027 au soir au lundi 29 mars 2027 au matin")
                .contains("href=\"mailto:coordination@example.org\"");

        browser.get("/admin/saison");
        browser.post("/admin/saison", form(FIRST, LAST, "organizer", " "));
        assertThat(jdbc.queryForObject("SELECT organizer FROM season", String.class)).isNull();
        assertThat(new Browser(mockMvc).get("/").getContentAsString())
                .doesNotContain("Site d’inscription à Hiver Solidaire");
    }

    @Test
    void an_organizer_too_long_or_with_control_characters_is_refused() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        create(browser, "organizer", "Paroisse Saint-Exemple");
        browser.get("/admin/saison");

        for (var invalid : new String[] {"x".repeat(101), "Saint\nExemple", "Saint\u0000Exemple"}) {
            var refused = browser.post("/admin/saison", form(FIRST, LAST, "organizer", invalid));
            assertThat(refused.getStatus()).isEqualTo(200);
            assertThat(refused.getContentAsString()).contains("L’organisateur tient sur une ligne");
        }

        assertThat(jdbc.queryForObject("SELECT organizer FROM season", String.class))
                .isEqualTo("Paroisse Saint-Exemple");
        assertThat(audits(coordinator.id(), "SEASON_UPDATED")).isZero();
    }

    @Test
    void the_sample_season_is_not_replaced_while_it_has_active_registrations() throws Exception {
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LocalDate.of(2027, 1, 20), "NIGHT_AND_DINNER");
        var browser = volunteers.coordinator().browser();

        assertThat(browser.get("/admin/saison/nouvelle").getContentAsString())
                .contains("La saison d’exemple a encore 1 inscription active")
                .doesNotContain("action=\"/admin/saison/nouvelle\"");
        var refused = browser.post("/admin/saison/nouvelle", form(FIRST, LAST));
        assertThat(refused.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM season WHERE sample", Integer.class)).isEqualTo(1);

        // Une fois l'inscription annulée, la saison d'exemple est remplacée, et l'inscription annulée part avec elle.
        anne.browser().get("/mes-inscriptions/annuler?soiree=2027-01-20&choix=NIGHT_AND_DINNER");
        anne.browser().post("/mes-inscriptions/annuler", "soiree", "2027-01-20", "choix", "NIGHT_AND_DINNER");
        assertThat(create(browser).getRedirectedUrl()).isEqualTo("/admin/saison");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM season WHERE sample", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration", Integer.class)).isZero();
    }

    @Test
    void a_date_change_never_removes_an_evening_with_active_registrations() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        create(browser);
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), LAST, "NIGHT_AND_DINNER");
        browser.get("/admin/saison");

        var refused = browser.post("/admin/saison", form(FIRST, LAST.minusDays(7)));

        assertThat(refused.getStatus()).isEqualTo(200);
        assertThat(refused.getContentAsString()).contains("Ces dates ne peuvent pas sortir de la saison")
                .contains("dim. 28 mars");
        assertThat(evenings()).isEqualTo(84);
        assertThat(jdbc.queryForObject("SELECT last_evening FROM season", LocalDate.class)).isEqualTo(LAST);
        assertThat(audits(coordinator.id(), "SEASON_UPDATED")).isZero();

        // Sans inscription, les soirées sortantes sont supprimées et les nouvelles créées.
        var updated = browser.post("/admin/saison", form(FIRST.plusDays(3), LAST.plusDays(2)));
        assertThat(updated.getRedirectedUrl()).isEqualTo("/admin/saison");
        assertThat(evenings()).isEqualTo(84 - 3 + 2);
        assertThat(jdbc.queryForObject("SELECT min(evening_date) FROM evening", LocalDate.class))
                .isEqualTo(FIRST.plusDays(3));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE cancelled_at IS NULL", Integer.class))
                .isEqualTo(1);
        assertThat(audits(coordinator.id(), "SEASON_UPDATED")).isEqualTo(1);
    }

    @Test
    void an_invalid_season_is_refused_without_any_change() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();

        var response = create(browser, "lastEvening", "2026-12-01", "needs[4].minimum", "3",
                "coordinatorsContact", "appelez-nous");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Indiquez une première et une dernière soirée")
                .contains("le maximum au moins égal au minimum")
                .contains("Indiquez un numéro de téléphone français ou une adresse e-mail");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM season WHERE sample", Integer.class)).isEqualTo(1);
        assertThat(audits(coordinator.id(), "SEASON_CREATED")).isZero();
    }

    @Test
    void a_contact_that_is_neither_a_french_number_nor_an_email_address_is_refused() throws Exception {
        var coordinator = volunteers.coordinator();
        var browser = coordinator.browser();
        create(browser, "coordinatorsContact", "01 99 00 56 78");
        browser.get("/admin/saison");

        for (var invalid : new String[] {"+44 20 7946 0000", "a?bcc=autre@example.org", "x".repeat(255)}) {
            var refused = browser.post("/admin/saison", form(FIRST, LAST, "coordinatorsContact", invalid));
            assertThat(refused.getStatus()).isEqualTo(200);
            assertThat(refused.getContentAsString())
                    .contains("Indiquez un numéro de téléphone français ou une adresse e-mail");
        }

        assertThat(jdbc.queryForObject("SELECT coordinators_contact FROM season", String.class))
                .isEqualTo("01 99 00 56 78");
        assertThat(audits(coordinator.id(), "SEASON_UPDATED")).isZero();
    }

    @Test
    void the_season_phone_reaches_volunteers_but_never_the_public_planning() throws Exception {
        create(volunteers.coordinator().browser(), "coordinatorsContact", "01 99 00 56 78");
        var anne = volunteers.volunteer();
        // Soirée du jour même : la limite de R6 est passée, le contact des coordinateurs s'affiche.
        TestVolunteers.register(anne.browser(), FIRST, "NIGHT_AND_DINNER");

        var mine = anne.browser().get("/mes-inscriptions").getContentAsString();

        assertThat(mine).contains("Pour vous désinscrire, contactez les coordinateurs via")
                .contains("href=\"tel:0199005678\"").contains("01 99 00 56 78").doesNotContain("Le lieu d’accueil");
        var publicPage = new Browser(mockMvc).get("/planning").getContentAsString();
        assertThat(publicPage).doesNotContain("01 99 00 56 78").doesNotContain("0199005678");
    }

    @Test
    void an_email_address_as_contact_gets_a_mailto_link() throws Exception {
        var browser = volunteers.coordinator().browser();
        create(browser, "coordinatorsContact", "01 99 00 56 78");
        browser.get("/admin/saison");
        var updated = browser.post("/admin/saison",
                form(FIRST, LAST, "coordinatorsContact", " Coordination@Example.org "));
        assertThat(updated.getRedirectedUrl()).isEqualTo("/admin/saison");
        assertThat(jdbc.queryForObject("SELECT coordinators_contact FROM season", String.class))
                .isEqualTo("coordination@example.org");
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), FIRST, "NIGHT_AND_DINNER");

        var mine = anne.browser().get("/mes-inscriptions").getContentAsString();
        var confirm = anne.browser().get("/mes-inscriptions/annuler?soiree=" + FIRST + "&choix=NIGHT_AND_DINNER")
                .getContentAsString();

        for (var page : new String[] {mine, confirm}) {
            assertThat(page).contains("Pour vous désinscrire, contactez les coordinateurs via")
                    .contains("href=\"mailto:coordination@example.org\"").doesNotContain("tel:");
        }
        var publicPage = new Browser(mockMvc).get("/planning").getContentAsString();
        assertThat(publicPage).doesNotContain("coordination@example.org");
    }

    @Test
    void without_a_season_contact_volunteers_are_told_to_contact_a_coordinator_without_a_link() throws Exception {
        create(volunteers.coordinator().browser(), "coordinatorsContact", "");
        var anne = volunteers.volunteer();
        TestVolunteers.register(anne.browser(), FIRST, "NIGHT_AND_DINNER");

        var mine = anne.browser().get("/mes-inscriptions").getContentAsString();
        var confirm = anne.browser().get("/mes-inscriptions/annuler?soiree=" + FIRST + "&choix=NIGHT_AND_DINNER")
                .getContentAsString();

        for (var page : new String[] {mine, confirm}) {
            assertThat(page).contains("Contactez un coordinateur.").doesNotContain("href=\"tel:")
                    .doesNotContain("href=\"mailto:").doesNotContain("contactez les coordinateurs via");
        }
    }

    @Test
    void the_dashboard_reports_a_missing_season_contact_until_it_is_filled_in() throws Exception {
        var browser = volunteers.coordinator().browser();
        // La saison d'exemple n'a pas de contact.
        assertThat(browser.get("/admin").getContentAsString())
                .contains("Le contact des coordinateurs n’est pas renseigné").contains("href=\"/admin/saison\"");

        create(browser, "coordinatorsContact", "");
        assertThat(browser.get("/admin").getContentAsString())
                .contains("Le contact des coordinateurs n’est pas renseigné");

        browser.get("/admin/saison");
        browser.post("/admin/saison", form(FIRST, LAST, "coordinatorsContact", "coordination@example.org"));
        assertThat(browser.get("/admin").getContentAsString())
                .doesNotContain("Le contact des coordinateurs n’est pas renseigné");
    }

    @Test
    void the_dashboard_reports_a_missing_organizer_named_by_the_privacy_notice() throws Exception {
        var browser = volunteers.coordinator().browser();
        // La saison d'exemple n'a pas d'organisateur : la notice ne nomme pas le responsable du traitement.
        assertThat(browser.get("/admin").getContentAsString())
                .contains("L’organisateur de la saison n’est pas renseigné");

        create(browser, "organizer", "Association factice des tests");
        assertThat(browser.get("/admin").getContentAsString())
                .doesNotContain("L’organisateur de la saison n’est pas renseigné");
    }

    @Test
    void the_planning_shows_no_season_name_only_a_notice_for_the_sample_season() throws Exception {
        var before = new Browser(mockMvc).get("/planning").getContentAsString();
        assertThat(before).contains("Saison d’exemple : les dates sont à confirmer.")
                .doesNotContain("(dates à confirmer)");

        create(volunteers.coordinator().browser());

        var after = new Browser(mockMvc).get("/planning").getContentAsString();
        assertThat(after).doesNotContain("Saison d’exemple").doesNotContain("class=\"saison\"");
    }
}
