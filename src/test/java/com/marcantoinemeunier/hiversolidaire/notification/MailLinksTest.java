package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.MutableClock;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.RecordingMailTransport;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;
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
 * Aucun suivi (S5a, décision 6) : dans les quatre e-mails (confirmation, rappel, alerte, récap), chaque lien pointe
 * directement vers {@code APP_BASE_URL}, sans paramètre de suivi, et la version HTML ne charge rien.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class MailLinksTest {

    private static final LocalDate TUESDAY = LocalDate.of(2027, 1, 5);
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"<>]+");
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]*)\"");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    RecordingMailTransport mails;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    MailJobs jobs;

    private String coordinator;

    @BeforeEach
    void setUp() {
        coordinator = "coordination-" + UUID.randomUUID() + "@example.org";
        coordinators.add(coordinator);
        jdbc.update("DELETE FROM mail_delivery");
        mails.clear();
    }

    @AfterEach
    void clean() {
        coordinators.remove(coordinator);
        mails.clear();
        jdbc.update("DELETE FROM mail_delivery");
        clock.set(TestClockAndMailConfiguration.DURING_SEASON);
        jdbc.update("DELETE FROM coordinator_alert WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                TUESDAY);
        jdbc.update("DELETE FROM registration WHERE evening_id = (SELECT id FROM evening WHERE evening_date = ?)",
                TUESDAY);
    }

    private void at(int hour, int minute) {
        clock.set(LocalDateTime.of(2027, 1, 4, hour, minute).atZone(ParisTime.ZONE).toInstant());
        jobs.tick();
    }

    @Test
    void every_link_of_every_mail_goes_straight_to_the_application() throws Exception {
        var anne = new TestVolunteers(mockMvc, sender, coordinators, jdbc).volunteer();
        TestVolunteers.register(anne.browser(), TUESDAY, "NIGHT_AND_DINNER");
        TestVolunteers.register(anne.browser(), TUESDAY, "BREAKFAST");
        // Désinscription d'un poste de mardi soir (dîner dans 33 h) : alerte immédiate.
        anne.browser().get("/mes-inscriptions/annuler?soiree=" + TUESDAY + "&choix=BREAKFAST");
        anne.browser().post("/mes-inscriptions/annuler", "soiree", TUESDAY.toString(), "choix", "BREAKFAST");

        at(10, 0);
        at(10, 30);
        at(18, 0);
        at(20, 0);

        var subjects = mails.sent().stream().map(mail -> mail.subject()).toList();
        assertThat(subjects).anyMatch(subject -> subject.startsWith("Vos inscriptions"))
                .anyMatch(subject -> subject.startsWith("Rappel"))
                .anyMatch(subject -> subject.startsWith("Soirée du"))
                .anyMatch(subject -> subject.startsWith("Récap"));
        for (var mail : mails.sent()) {
            var links = new ArrayList<String>();
            URL.matcher(mail.text()).results().forEach(match -> links.add(match.group()));
            URL.matcher(mail.html()).results().forEach(match -> links.add(match.group()));
            HREF.matcher(mail.html()).results().forEach(match -> links.add(match.group(1)));

            assertThat(links).as(mail.subject()).isNotEmpty().allSatisfy(link -> assertThat(link)
                    .startsWith("http://localhost:8080/")
                    .doesNotContainIgnoringCase("utm_")
                    .doesNotContainIgnoringCase("track"));
            assertThat(mail.html()).as(mail.subject()).doesNotContain("<img").doesNotContain("src=");
        }
    }
}
