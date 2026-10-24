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

/** Page publique du planning, sur la saison d'exemple de la migration V3 (76 soirées, 4 janv. → 20 mars 2027). */
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
        var response = mockMvc.perform(get("/planning")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsString();
    }

    private void register(LocalDate eveningDate, String type, String dishChoice) {
        jdbc.update("""
                INSERT INTO registration (id, evening_id, type, dish_choice)
                SELECT gen_random_uuid(), e.id, ?, ? FROM evening e WHERE e.evening_date = ?
                """, type, dishChoice, eveningDate);
    }

    private static int count(String body, String text) {
        return body.split(Pattern.quote(text), -1).length - 1;
    }

    @Test
    void lists_every_evening_of_the_sample_season_marked_as_sample() throws Exception {
        var body = page();

        assertThat(body).contains("Planning de la saison");
        assertThat(body).contains("Dates d’exemple");
        assertThat(count(body, "class=\"soiree ")).isEqualTo(76);
        assertThat(body).contains("Lun. 4 → mar. 5 janv.");
        assertThat(body).contains("Sam. 20 → dim. 21 mars");
        assertThat(count(body, "Personne pour l’instant")).isEqualTo(76);
        assertThat(body).as("une soirée vide ne liste pas tous les postes").doesNotContain("Manque");
    }

    @Test
    void shows_complete_partial_and_empty_evenings() throws Exception {
        var complete = LocalDate.of(2027, 1, 4);
        register(complete, "PREPARE_AND_SHARE", "MAIN_DISH");
        register(complete, "PREPARE_AND_SHARE", "SIDES");
        register(complete, "NIGHT_AND_DINNER", null);
        register(complete, "BREAKFAST", null);
        var partial = LocalDate.of(2027, 1, 5);
        register(partial, "PREPARE_AND_SHARE", "MAIN_DISH");
        register(partial, "PICK_UP_AND_SHARE", null);

        var body = page();

        assertThat(count(body, "soiree--complete")).isEqualTo(1);
        assertThat(count(body, "soiree--partial")).isEqualTo(1);
        assertThat(count(body, "soiree--empty")).isEqualTo(74);
        assertThat(body).contains("Complète");
        assertThat(body).contains("Manque : accompagnements, nuit, petit-déjeuner");
    }

    @Test
    void closed_evening_is_shown_as_closed() throws Exception {
        jdbc.update("UPDATE evening SET closed = true WHERE evening_date = ?", LocalDate.of(2027, 1, 6));

        var body = page();

        assertThat(count(body, "soiree--closed")).isEqualTo(1);
        assertThat(body).contains("Fermée");
    }

    @Test
    void past_evenings_are_hidden() throws Exception {
        now(LocalDateTime.of(2027, 1, 10, 9, 0));

        var body = page();

        assertThat(body).doesNotContain("Lun. 4 → mar. 5 janv.");
        assertThat(body).doesNotContain("Sam. 9 → dim. 10 janv.");
        assertThat(body).contains("Dim. 10 → lun. 11 janv.");
        assertThat(count(body, "class=\"soiree ")).isEqualTo(70);
    }

    @Test
    void after_the_season_no_evening_is_listed() throws Exception {
        now(LocalDateTime.of(2027, 3, 21, 9, 0));

        var body = page();

        assertThat(count(body, "class=\"soiree ")).isZero();
        assertThat(body).contains("Aucune soirée à venir");
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
}
