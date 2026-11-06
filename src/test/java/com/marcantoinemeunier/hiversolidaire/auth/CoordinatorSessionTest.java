package com.marcantoinemeunier.hiversolidaire.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.time.Duration;
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
 * Rôle coordinateur (ADR-0010) et sessions de 8 h (cahier des charges §8) : le rôle est décidé à chaque connexion
 * d'après la liste des adresses, et la session d'un coordinateur expire 8 h après la connexion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class CoordinatorSessionTest {

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

    @Test
    void a_coordinator_session_is_kept_until_8_hours() throws Exception {
        var browser = volunteers.coordinator().browser();

        clock.advance(Duration.ofHours(8).minusMinutes(1));

        assertThat(browser.get("/admin").getStatus()).isEqualTo(200);
    }

    @Test
    void a_coordinator_session_is_refused_after_8_hours() throws Exception {
        var browser = volunteers.coordinator().browser();
        assertThat(browser.get("/admin").getStatus()).isEqualTo(200);

        clock.advance(Duration.ofHours(8));

        assertThat(browser.get("/admin").getStatus()).isEqualTo(403);
        // La session est fermée tout entière : le coordinateur se reconnecte, y compris comme bénévole.
        assertThat(browser.get("/mes-inscriptions").getRedirectedUrl()).endsWith("/connexion");
    }

    @Test
    void a_volunteer_session_is_still_kept_beyond_8_hours() throws Exception {
        var browser = volunteers.volunteer().browser();

        clock.advance(Duration.ofHours(9));

        assertThat(browser.get("/mes-inscriptions").getStatus()).isEqualTo(200);
    }

    @Test
    void a_coordinator_is_also_a_volunteer() throws Exception {
        var browser = volunteers.coordinator().browser();

        assertThat(browser.get("/mes-inscriptions").getStatus()).isEqualTo(200);
        assertThat(browser.get("/mon-compte").getStatus()).isEqualTo(200);
    }

    @Test
    void an_address_removed_from_the_list_loses_the_role_at_the_next_sign_in() throws Exception {
        var coordinator = volunteers.coordinator();
        assertThat(coordinator.browser().get("/admin").getStatus()).isEqualTo(200);

        coordinators.remove(coordinator.email());
        var again = volunteers.signIn(coordinator.email(), false);

        assertThat(again.id()).isEqualTo(coordinator.id());
        assertThat(again.landing()).isEqualTo("/mes-inscriptions");
        assertThat(again.browser().get("/admin").getStatus()).isEqualTo(403);
        assertThat(again.browser().get("/mes-inscriptions").getStatus()).isEqualTo(200);
    }

    @Test
    void an_address_added_to_the_list_gets_the_role_at_the_next_sign_in() throws Exception {
        var volunteer = volunteers.volunteer();
        assertThat(volunteer.browser().get("/admin").getStatus()).isEqualTo(403);

        coordinators.add(volunteer.email());
        var again = volunteers.signIn(volunteer.email(), false);

        assertThat(again.browser().get("/admin").getStatus()).isEqualTo(200);
    }
}
