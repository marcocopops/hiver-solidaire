package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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
 * Page d'accueil (lot 4) : présentation des trois façons de participer, dates de la saison en base, organisateur et
 * contact des coordinateurs venant des réglages de la saison (jamais du dépôt). Sans organisateur ou sans contact, la
 * phrase correspondante disparaît.
 *
 * <p>Les tests qui renseignent la saison la remettent à vide après (base partagée avec les autres tests).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class HomePageTest {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+33|\\b0)[1-9]([ .-]?[0-9]{2}){4}");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clearSeason() {
        jdbc.update("UPDATE season SET organizer = NULL, coordinators_contact = NULL");
    }

    private String home() throws Exception {
        return new Browser(mockMvc).get("/").getContentAsString();
    }

    @Test
    void the_home_page_is_public_and_presents_the_three_ways_to_take_part() throws Exception {
        var html = home();

        assertThat(html).contains("lang=\"fr\"")
                .contains("Le petit-déjeuner").contains("Le dîner").contains("La nuit")
                .contains("repas pour 8 personnes")
                .contains("<a class=\"bouton\" href=\"/planning\">Voir le planning</a>")
                .doesNotContain("Inscriptions à partir du");
        // Le bouton vers le planning, avant la présentation et à la fin : pas besoin de faire défiler.
        var button = "<a class=\"bouton\" href=\"/planning\">Voir le planning</a>";
        assertThat(html.split(button, -1)).as("deux boutons vers le planning").hasSize(3);
        assertThat(html.indexOf(button)).isLessThan(html.indexOf("Trois façons de participer"));
        assertThat(html).doesNotContain("<script").doesNotContain("<style").doesNotContain("style=");
    }

    @Test
    void the_dates_come_from_the_season() throws Exception {
        var first = jdbc.queryForObject("SELECT first_evening FROM season", java.time.LocalDate.class);
        var last = jdbc.queryForObject("SELECT last_evening FROM season", java.time.LocalDate.class);
        var format = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH);

        assertThat(home()).contains("Hiver Solidaire accueillera du " + format.format(first) + " au soir au "
                + format.format(last.plusDays(1)) + " au matin");
    }

    @Test
    void without_organizer_nor_contact_their_sentences_are_left_out() throws Exception {
        var html = home();

        assertThat(html).doesNotContain("Site d’inscription à Hiver Solidaire")
                .doesNotContain("En cas de question").doesNotContain("mailto:").doesNotContain("tel:");
    }

    @Test
    void the_organizer_and_the_contact_come_from_the_season_settings() throws Exception {
        jdbc.update("UPDATE season SET organizer = ?, coordinators_contact = ?", "Paroisse Saint-Exemple",
                "coordination@example.org");

        var html = home();

        assertThat(html).contains("Site d’inscription à Hiver Solidaire Paroisse Saint-Exemple")
                .contains("En cas de question")
                .contains("<a href=\"mailto:coordination@example.org\">coordination@example.org</a>");
    }

    @Test
    void the_organizer_is_shown_as_text_never_as_markup() throws Exception {
        jdbc.update("UPDATE season SET organizer = ?", "<b>Saint-Exemple</b>");

        assertThat(home()).contains("&lt;b&gt;Saint-Exemple&lt;/b&gt;").doesNotContain("<b>Saint-Exemple");
    }

    @Test
    void the_contact_alone_is_shown_without_the_organizer() throws Exception {
        jdbc.update("UPDATE season SET coordinators_contact = ?", "01 99 00 12 34");

        var html = home();

        assertThat(html).contains("<a href=\"tel:0199001234\">01 99 00 12 34</a>")
                .doesNotContain("Site d’inscription à Hiver Solidaire");
    }

    @Test
    void neither_the_template_nor_the_home_texts_hold_an_email_address_or_a_phone_number() throws IOException {
        var template = Files.readString(Path.of("src/main/resources/templates/index.html"), StandardCharsets.UTF_8);
        var messages = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/messages.properties"),
                StandardCharsets.UTF_8)) {
            messages.load(reader);
        }
        var homeTexts = new StringBuilder();
        messages.forEach((key, value) -> {
            if (key.toString().startsWith("home.")) {
                homeTexts.append(value).append('\n');
            }
        });

        for (var text : new String[] {template, homeTexts.toString()}) {
            assertThat(EMAIL.matcher(text).find()).as("adresse e-mail en dur").isFalse();
            assertThat(PHONE.matcher(text).find()).as("numéro de téléphone en dur").isFalse();
        }
    }
}
