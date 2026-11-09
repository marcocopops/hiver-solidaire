package com.marcantoinemeunier.hiversolidaire.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Back-office réservé au rôle coordinateur (cahier des charges §8, modèle de menaces M6, M7, M9) : chaque route
 * {@code /admin/**}, GET et POST, est refusée à un visiteur et à un bénévole. La liste des routes est lue dans les
 * correspondances de Spring MVC : une route ajoutée plus tard est couverte sans modifier ce test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class AdminAccessTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RequestMappingHandlerMapping mappings;

    TestVolunteers volunteers;

    @BeforeEach
    void setUp() {
        volunteers = new TestVolunteers(mockMvc, sender, coordinators, jdbc);
    }

    record Route(RequestMethod method, String path) {
    }

    /** Routes du back-office, variables de chemin remplacées par un identifiant quelconque. */
    private List<Route> adminRoutes() {
        var routes = new ArrayList<Route>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            var methods = info.getMethodsCondition().getMethods();
            for (var pattern : info.getPatternValues()) {
                if (!pattern.equals("/admin") && !pattern.startsWith("/admin/")) {
                    continue;
                }
                var path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                var effective = methods.isEmpty() ? List.of(RequestMethod.GET, RequestMethod.POST) : List.copyOf(methods);
                effective.forEach(method -> routes.add(new Route(method, path)));
            }
        });
        return routes;
    }

    @Test
    void the_back_office_has_routes_for_every_deliverable() {
        var paths = adminRoutes().stream().map(Route::path).toList();
        assertThat(paths).contains("/admin");
        assertThat(adminRoutes()).anyMatch(route -> route.method() == RequestMethod.POST);
    }

    @Test
    void every_admin_route_is_denied_to_a_visitor() throws Exception {
        for (var route : adminRoutes()) {
            var request = Stream.of(route).map(r -> r.method() == RequestMethod.GET
                    ? MockMvcRequestBuilders.get(r.path())
                    : MockMvcRequestBuilders.post(r.path()).with(csrf())).findFirst().orElseThrow();
            var response = mockMvc.perform(request).andReturn().getResponse();
            assertThat(response.getStatus()).as(route.toString()).isEqualTo(403);
        }
    }

    @Test
    void every_admin_route_is_denied_to_a_volunteer_who_is_not_a_coordinator() throws Exception {
        var browser = volunteers.volunteer().browser();
        browser.get("/mon-compte");
        for (var route : adminRoutes()) {
            var response = route.method() == RequestMethod.GET ? browser.get(route.path()) : browser.post(route.path());
            assertThat(response.getStatus()).as(route.toString()).isEqualTo(403);
            assertThat(response.getContentAsString()).as(route.toString()).doesNotContain("@example.org");
        }
    }

    @Test
    void every_admin_post_without_csrf_token_is_refused_even_to_a_coordinator() throws Exception {
        var browser = volunteers.coordinator().browser();
        for (var route : adminRoutes()) {
            if (route.method() == RequestMethod.POST) {
                assertThat(browser.postWithoutCsrf(route.path()).getStatus()).as(route.toString()).isEqualTo(403);
            }
        }
    }

    @Test
    void a_coordinator_reaches_the_dashboard() throws Exception {
        var browser = volunteers.coordinator().browser();

        var response = browser.get("/admin");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Tableau de bord");
    }

    @Test
    void the_back_office_links_are_shown_to_coordinators_only() throws Exception {
        assertThat(volunteers.coordinator().browser().get("/mes-inscriptions").getContentAsString())
                .contains("href=\"/admin\"").contains("href=\"/admin/benevoles\"")
                .contains("href=\"/admin/saison\"").contains("href=\"/admin/exports\"");
        var volunteer = volunteers.volunteer().browser();
        for (var path : List.of("/", "/planning", "/mes-inscriptions", "/mon-compte")) {
            assertThat(volunteer.get(path).getContentAsString()).as(path).doesNotContain("href=\"/admin");
        }
    }

    @Test
    void a_coordinator_lands_on_the_back_office_after_signing_in() throws Exception {
        var coordinator = volunteers.coordinator();

        var again = volunteers.signIn(coordinator.email(), false);

        assertThat(again.landing()).isEqualTo("/admin");
        assertThat(again.browser().get("/admin").getStatus()).isEqualTo(200);
    }
}
