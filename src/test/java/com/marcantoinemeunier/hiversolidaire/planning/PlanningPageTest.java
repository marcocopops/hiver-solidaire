package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Page publique du planning, sur la saison d'exemple de la migration V3 (76 soirées, 4 janv. → 20 mars 2027), donc
 * 77 journées (4 janv. → 21 mars au matin). Une carte par journée (ADR-0009), vue par un visiteur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PlanningPageTest {

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern PHONE = Pattern.compile("(?:\\+33|0)[1-9](?:[ .-]?\\d{2}){4}");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        now(LocalDateTime.of(2026, 12, 1, 12, 0));
    }

    private void now(LocalDateTime parisTime) {
        when(clock.instant()).thenReturn(parisTime.atZone(ParisTime.ZONE).toInstant());
        when(clock.getZone()).thenReturn(ParisTime.ZONE);
    }

    private String page() throws Exception {
        return page("/planning");
    }

    private String page(String url) throws Exception {
        var response = mockMvc.perform(get(url)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsString();
    }

    private void register(LocalDate eveningDate, String type, String dishChoice) {
        register(eveningDate, type, dishChoice, "P" + java.util.UUID.randomUUID().toString().substring(0, 8));
    }

    /** Inscription d'un bénévole au profil complet (nom de famille factice « Dupontsecret », jamais affiché). */
    private void register(LocalDate eveningDate, String type, String dishChoice, String pseudo) {
        var volunteerId = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO volunteer (id, email, last_name, first_name, phone, pseudo, profile_completed_at)
                VALUES (?, ?, 'Dupontsecret', 'Anne', '+33600000001', ?, now())
                """, volunteerId, volunteerId + "@example.org", pseudo);
        jdbc.update("""
                INSERT INTO registration (id, evening_id, volunteer_id, type, dish_choice)
                SELECT gen_random_uuid(), e.id, ?, ?, ? FROM evening e WHERE e.evening_date = ?
                """, volunteerId, type, dishChoice, eveningDate);
    }

    private static int count(String body, String text) {
        return body.split(Pattern.quote(text), -1).length - 1;
    }

    /** Bloc d'une journée sur la page, de sa date au début de la journée suivante. */
    private static String dayBlock(String body, String label) {
        var start = body.indexOf(">" + label + "<");
        assertThat(start).as(label).isPositive();
        var end = body.indexOf(DAY_CARD, start);
        return body.substring(start + 1, end < 0 ? body.length() : end);
    }

    /** Texte visible d'un fragment HTML, espaces regroupés. */
    private static String text(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").strip();
    }

    private static final String DAY_CARD = "<li class=\"journee\"";

    /** Dîner et nuit couverts sur la soirée {@code date}, sans petit-déjeuner. */
    private void fullDinnerAndNight(LocalDate date) {
        register(date, "PREPARE_AND_SHARE", "MAIN_DISH");
        register(date, "PREPARE_AND_SHARE", "SIDES");
        register(date, "NIGHT_AND_DINNER", null);
    }

    @Test
    void lists_one_card_per_day_without_the_sample_banner() throws Exception {
        var body = page();

        assertThat(body).contains("Planning de la saison");
        assertThat(count(body, DAY_CARD)).isEqualTo(77);
        assertThat(body).contains(">Lun. 4 janv.<").contains(">Dim. 21 mars<").doesNotContain("→");
        assertThat(body).doesNotContain("Dates d’exemple").doesNotContain("Personne pour l’instant")
                .doesNotContain("Manque");
    }

    @Test
    void every_day_without_registration_is_to_complete_and_open() throws Exception {
        var body = page();

        assertThat(count(body, "journee__carte journee--incomplete")).isEqualTo(77);
        assertThat(count(body, "open=\"open\"")).isEqualTo(77);
        assertThat(count(body, "À compléter</span>")).isEqualTo(77);
    }

    @Test
    void a_complete_day_is_folded_and_the_others_stay_open() throws Exception {
        // Premier jour : pas de petit-déjeuner, le dîner et la nuit suffisent.
        fullDinnerAndNight(LocalDate.of(2027, 1, 4));

        var body = page();

        assertThat(count(body, "journee__carte journee--complete")).isEqualTo(1);
        assertThat(count(body, "journee__carte journee--incomplete")).isEqualTo(76);
        assertThat(count(body, "open=\"open\"")).isEqualTo(76);
        assertThat(body).contains("<details class=\"journee__carte journee--complete\">");
        assertThat(text(dayBlock(body, "Lun. 4 janv."))).startsWith("Lun. 4 janv. Complète");
    }

    @Test
    void a_day_is_complete_with_the_previous_evening_breakfast_and_its_own_dinner_and_night() throws Exception {
        register(LocalDate.of(2027, 1, 4), "BREAKFAST", null);
        fullDinnerAndNight(LocalDate.of(2027, 1, 5));
        // Le petit-déjeuner de la soirée du 5 compte pour le 6, pas pour le 5.
        register(LocalDate.of(2027, 1, 5), "BREAKFAST", null);

        var body = page();

        assertThat(text(dayBlock(body, "Mar. 5 janv."))).startsWith("Mar. 5 janv. Complète");
        assertThat(text(dayBlock(body, "Mer. 6 janv."))).startsWith("Mer. 6 janv. À compléter");
    }

    @Test
    void the_first_day_has_no_breakfast_and_the_last_one_only_breakfast() throws Exception {
        var body = page();

        assertThat(text(dayBlock(body, "Lun. 4 janv."))).doesNotContain("Petit-déjeuner").contains("Nuit");
        assertThat(text(dayBlock(body, "Dim. 21 mars"))).contains("Petit-déjeuner")
                .doesNotContain("Plat principal").doesNotContain("Dîner").doesNotContain("Nuit");
    }

    @Test
    void posts_are_listed_breakfast_main_dish_sides_dinner_then_night() throws Exception {
        var block = text(dayBlock(page(), "Mar. 5 janv."));

        assertThat(block).containsSubsequence("Petit-déjeuner", "Plat principal", "Accompagnements", "Dîner",
                "Nuit jusqu’au matin");
    }

    @Test
    void the_breakfast_of_an_evening_shows_on_the_next_day() throws Exception {
        register(LocalDate.of(2027, 1, 5), "BREAKFAST", null, "Léa");

        var body = page();

        assertThat(text(dayBlock(body, "Mer. 6 janv."))).contains("Petit-déjeuner Léa");
        assertThat(text(dayBlock(body, "Mar. 5 janv."))).doesNotContain("Léa");
    }

    @Test
    void shows_the_pseudos_by_post_and_the_places_to_fill() throws Exception {
        var date = LocalDate.of(2027, 1, 5);
        register(date.minusDays(1), "BREAKFAST", null, "Léa");
        register(date, "PREPARE_AND_SHARE", "MAIN_DISH", "MarieD");
        register(date, "PREPARE_AT_HOME", "SIDES", "Paul_B");
        register(date, "NIGHT_AND_DINNER", null, "Jean-L");
        register(date, "NIGHT_AND_DINNER", null, "Alain");

        var block = text(dayBlock(page(), "Mar. 5 janv."));

        assertThat(block).contains("Petit-déjeuner Léa")
                .contains("Plat principal MarieD")
                .contains("Accompagnements Paul_B")
                .contains("Dîner MarieD À pourvoir")
                .contains("Nuit jusqu’au matin Alain Jean-L");
    }

    @Test
    void an_empty_post_shows_one_place_to_fill_per_missing_volunteer() throws Exception {
        var block = text(dayBlock(page(), "Mar. 5 janv."));

        assertThat(block).contains("Dîner À pourvoir À pourvoir").contains("Nuit jusqu’au matin À pourvoir");
        assertThat(count(block, "À pourvoir")).isEqualTo(6);
    }

    @Test
    void past_days_are_hidden_and_today_breakfast_is_still_shown() throws Exception {
        register(LocalDate.of(2027, 1, 9), "BREAKFAST", null, "Léa");
        now(LocalDateTime.of(2027, 1, 10, 9, 0));

        var body = page();

        assertThat(body).doesNotContain(">Lun. 4 janv.<").doesNotContain(">Sam. 9 janv.<");
        assertThat(count(body, DAY_CARD)).isEqualTo(71);
        assertThat(text(dayBlock(body, "Dim. 10 janv."))).contains("Petit-déjeuner Léa");
    }

    @Test
    void the_day_after_the_last_evening_is_shown_until_midnight() throws Exception {
        now(LocalDateTime.of(2027, 3, 21, 9, 0));
        assertThat(count(page(), DAY_CARD)).isEqualTo(1);

        now(LocalDateTime.of(2027, 3, 22, 0, 0));
        var body = page();
        assertThat(count(body, DAY_CARD)).isZero();
        assertThat(body).contains("Aucune journée à venir");
    }

    @Test
    void a_visitor_can_join_a_day_once_registrations_are_open() throws Exception {
        assertThat(page()).doesNotContain("Je participe");

        now(LocalDateTime.of(2027, 1, 4, 10, 0));
        var body = page();

        assertThat(dayBlock(body, "Mar. 5 janv.")).contains("href=\"/inscription?jour=2027-01-05\"")
                .contains("Je participe");
        assertThat(body).doesNotContain("Me désinscrire").doesNotContain(">Vous<");
    }

    @Test
    void a_visitor_gets_all_and_to_complete_filters_but_not_mine() throws Exception {
        var body = page();

        assertThat(body).contains("<a href=\"/planning\" aria-current=\"page\">Toutes</a>")
                .contains("<a href=\"/planning?filtre=a-completer\">À compléter</a>")
                .doesNotContain("filtre=miennes").doesNotContain("Les miennes");
    }

    @Test
    void the_to_complete_filter_hides_complete_days() throws Exception {
        fullDinnerAndNight(LocalDate.of(2027, 1, 4));

        var body = page("/planning?filtre=a-completer");

        assertThat(count(body, DAY_CARD)).isEqualTo(76);
        assertThat(body).doesNotContain(">Lun. 4 janv.<")
                .contains("<a href=\"/planning?filtre=a-completer\" aria-current=\"page\">À compléter</a>");
    }

    @Test
    void the_mine_filter_falls_back_to_all_for_a_visitor() throws Exception {
        var body = page("/planning?filtre=miennes");

        assertThat(count(body, DAY_CARD)).isEqualTo(77);
        assertThat(body).contains("<a href=\"/planning\" aria-current=\"page\">Toutes</a>");
    }

    @Test
    void an_unknown_filter_falls_back_to_all_without_echoing_it() throws Exception {
        var body = page("/planning?filtre=%3Cscript%3Ealert(1)%3C%2Fscript%3E");

        assertThat(count(body, DAY_CARD)).isEqualTo(77);
        assertThat(body).contains("<a href=\"/planning\" aria-current=\"page\">Toutes</a>")
                .doesNotContain("<script").doesNotContain("alert(1)");
    }

    @Test
    void page_leaks_no_identifier_nor_personal_data() throws Exception {
        register(LocalDate.of(2027, 1, 4), "BREAKFAST", null);

        var body = page();

        assertThat(UUID.matcher(body).find()).as("identifiant technique").isFalse();
        assertThat(PHONE.matcher(body).find()).as("numéro de téléphone").isFalse();
        assertThat(body).doesNotContain("@");
        assertThat(body.toLowerCase()).doesNotContain("adresse");
    }

    @Test
    void the_pseudos_never_come_with_other_personal_data() throws Exception {
        register(LocalDate.of(2027, 1, 4), "BREAKFAST", null, "Léa");
        register(LocalDate.of(2027, 1, 5), "NIGHT_AND_DINNER", null, "MarieD");

        var body = page();

        assertThat(body).contains("Léa").contains("MarieD").doesNotContain("Dupontsecret")
                .doesNotContain("@").doesNotContain("+33");
        assertThat(UUID.matcher(body).find()).as("identifiant technique").isFalse();
        assertThat(PHONE.matcher(body).find()).as("numéro de téléphone").isFalse();
    }

    @Test
    void page_has_no_inline_script_or_style() throws Exception {
        assertThat(page())
                .doesNotContain("<script")
                .doesNotContain("<style")
                .doesNotContain("style=");
    }

    @Test
    void home_page_links_to_the_planning() throws Exception {
        var body = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("href=\"/planning\"");
    }

    @Test
    void the_planning_asks_search_engines_not_to_index_it() throws Exception {
        var response = mockMvc.perform(get("/planning")).andReturn().getResponse();

        assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");
        assertThat(response.getContentAsString()).contains("<meta name=\"robots\" content=\"noindex, nofollow\">");
    }
}
