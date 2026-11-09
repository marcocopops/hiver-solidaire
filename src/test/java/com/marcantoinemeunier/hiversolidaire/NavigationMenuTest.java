package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Menu « burger » de l'en-tête (lot 4) : un {@code <details>} sans JavaScript, les mêmes entrées dans le même ordre
 * sur toutes les pages, selon le visiteur, le bénévole ou le coordinateur ; l'entrée de la page en cours porte
 * {@code aria-current="page"}. Les pages coordinateur ont un en-tête prune avec la mention « Accès coordinateur ».
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class NavigationMenuTest {

    private static final Pattern ENTRY = Pattern.compile(
            "<(a|button)\\b([^>]*)>\\s*(?:<svg.*?</svg>\\s*)?([^<]+?)\\s*</\\1>", Pattern.DOTALL);

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

    record Entry(String label, String attributes) {
        boolean current() {
            return attributes.contains("aria-current=\"page\"");
        }

        boolean coordinator() {
            return attributes.contains("menu__lien--coordinateur");
        }
    }

    private static String header(String html) {
        return html.substring(html.indexOf("<header"), html.indexOf("</header>"));
    }

    private static String menu(String html) {
        var header = header(html);
        var start = header.indexOf("<details class=\"menu\"");
        assertThat(start).as("menu dans l'en-tête").isNotNegative();
        return header.substring(start, header.indexOf("</details>", start));
    }

    private static List<Entry> entries(String html) {
        var nav = menu(html);
        var matcher = ENTRY.matcher(nav.substring(nav.indexOf("<nav")));
        var entries = new ArrayList<Entry>();
        while (matcher.find()) {
            entries.add(new Entry(matcher.group(3), matcher.group(2)));
        }
        return entries;
    }

    private static List<String> labels(String html) {
        return entries(html).stream().map(Entry::label).toList();
    }

    private static List<String> current(String html) {
        return entries(html).stream().filter(Entry::current).map(Entry::label).toList();
    }

    @Test
    void the_menu_is_a_details_element_with_a_labelled_navigation() throws Exception {
        var menu = menu(new Browser(mockMvc).get("/planning").getContentAsString());

        assertThat(menu).contains("<summary class=\"menu__bouton\">").contains("Menu")
                .contains("<nav class=\"menu__liste\" aria-label=\"Menu principal\">");
        assertThat(menu).doesNotContain("<script");
    }

    @Test
    void a_visitor_sees_the_planning_and_the_sign_in() throws Exception {
        var html = new Browser(mockMvc).get("/").getContentAsString();

        assertThat(labels(html)).containsExactly("Planning", "Me connecter");
        assertThat(menu(html)).contains("href=\"/planning\"").contains("href=\"/connexion\"");
    }

    @Test
    void the_sign_in_pages_leave_out_the_sign_in_entry() throws Exception {
        var html = new Browser(mockMvc).get("/connexion").getContentAsString();

        assertThat(labels(html)).containsExactly("Planning");
    }

    @Test
    void a_volunteer_sees_their_entries_and_signs_out_with_a_post() throws Exception {
        var html = volunteers.volunteer().browser().get("/planning").getContentAsString();

        assertThat(labels(html)).containsExactly("Planning", "Mes inscriptions", "Mon compte", "Se déconnecter");
        assertThat(entries(html)).noneMatch(Entry::coordinator);
        assertThat(menu(html)).doesNotContain("/admin").contains("action=\"/deconnexion\" method=\"post\"")
                .contains("name=\"_csrf\"");
    }

    @Test
    void a_coordinator_sees_the_volunteer_entries_then_their_own_before_signing_out() throws Exception {
        var html = volunteers.coordinator().browser().get("/planning").getContentAsString();

        assertThat(labels(html)).containsExactly("Planning", "Mes inscriptions", "Mon compte", "Tableau de bord",
                "Bénévoles", "Saison", "Exports", "Se déconnecter");
        assertThat(entries(html).stream().filter(Entry::coordinator).map(Entry::label))
                .containsExactly("Tableau de bord", "Bénévoles", "Saison", "Exports");
        assertThat(menu(html)).contains("href=\"/admin\"").contains("href=\"/admin/benevoles\"")
                .contains("href=\"/admin/saison\"").contains("href=\"/admin/exports\"");
    }

    @Test
    void the_entry_of_the_current_page_is_marked() throws Exception {
        var visitor = new Browser(mockMvc);
        assertThat(current(visitor.get("/planning").getContentAsString())).containsExactly("Planning");
        assertThat(current(visitor.get("/planning?filtre=a-completer").getContentAsString()))
                .containsExactly("Planning");
        assertThat(current(visitor.get("/").getContentAsString())).isEmpty();

        var coordinator = volunteers.coordinator().browser();
        assertThat(current(coordinator.get("/mes-inscriptions").getContentAsString()))
                .containsExactly("Mes inscriptions");
        assertThat(current(coordinator.get("/mon-compte").getContentAsString())).containsExactly("Mon compte");
        assertThat(current(coordinator.get("/admin").getContentAsString())).containsExactly("Tableau de bord");
        assertThat(current(coordinator.get("/admin/benevoles").getContentAsString())).containsExactly("Bénévoles");
        assertThat(current(coordinator.get("/admin/saison/nouvelle").getContentAsString()))
                .containsExactly("Saison");
        assertThat(current(coordinator.get("/admin/exports").getContentAsString())).containsExactly("Exports");
        // Pages du back-office sans entrée propre (journée, inscriptions…) : le tableau de bord.
        assertThat(current(coordinator.get("/admin/journee?jour=2027-01-13").getContentAsString()))
                .containsExactly("Tableau de bord");
    }

    @Test
    void the_old_navigation_bars_are_gone() throws Exception {
        var coordinator = volunteers.coordinator().browser();
        for (var path : List.of("/mes-inscriptions", "/mon-compte", "/admin", "/admin/benevoles")) {
            var html = coordinator.get(path).getContentAsString();
            assertThat(html).as(path).doesNotContain("class=\"nav").doesNotContain("Mon espace")
                    .doesNotContain("Planning public").doesNotContain("Back-office");
            assertThat(html.split("action=\"/deconnexion\"", -1)).as(path + " : une seule déconnexion").hasSize(2);
        }
    }

    @Test
    void the_admin_pages_carry_the_coordinator_header() throws Exception {
        var coordinator = volunteers.coordinator().browser();
        for (var path : List.of("/admin", "/admin/benevoles", "/admin/saison/nouvelle", "/admin/exports",
                "/admin/journee?jour=2027-01-13")) {
            var header = header(coordinator.get(path).getContentAsString());
            assertThat(header).as(path).startsWith("<header class=\"entete entete--coordinateur\">")
                    .contains("Accès coordinateur");
        }
    }

    @Test
    void the_public_pages_keep_the_usual_header_even_for_a_coordinator() throws Exception {
        var coordinator = volunteers.coordinator().browser();
        for (var path : List.of("/", "/planning", "/mes-inscriptions", "/mon-compte")) {
            var html = coordinator.get(path).getContentAsString();
            assertThat(header(html)).as(path).startsWith("<header class=\"entete\">");
            assertThat(html).as(path).doesNotContain("entete--coordinateur").doesNotContain("Accès coordinateur");
        }
    }

    @Test
    void a_volunteer_refused_from_the_back_office_does_not_get_the_coordinator_header() throws Exception {
        var response = volunteers.volunteer().browser().get("/admin");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("Accès coordinateur")
                .doesNotContain("entete--coordinateur").doesNotContain("/admin/benevoles");
    }
}
