package com.marcantoinemeunier.hiversolidaire.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestCoordinatorEmails;
import com.marcantoinemeunier.hiversolidaire.TestVolunteers;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Notice d'information (cahier des charges §7, §11 question 7) : publique, en français clair, sans aucune donnée réelle
 * dans le dépôt. Le responsable est l'organisateur saisi pour la saison et le contact celui des coordinateurs ; vides,
 * la notice le dit sobrement. Les durées sont celles que la purge applique ({@link RetentionPolicy}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class PrivacyNoticeTest {

    private static final String NOTICE = "/donnees-personnelles";
    private static final String ORGANIZER = "Association factice des tests";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    TestCoordinatorEmails coordinators;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("UPDATE season SET organizer = NULL, coordinators_contact = NULL");
    }

    private String notice() throws Exception {
        var response = new Browser(mockMvc).get(NOTICE);
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsString();
    }

    @Test
    void the_notice_is_public_and_covers_what_the_volunteers_must_know() throws Exception {
        assertThat(notice())
                .contains("<h1>Vos données personnelles</h1>")
                // Pseudo public : visible par tous, pourquoi, comment le changer.
                .contains("Votre pseudo est public").contains("visible par tous").contains("href=\"/mon-compte\"")
                // Base légale, destinataires, sous-traitants, hébergement.
                .contains("intérêt légitime").contains("consentement")
                .contains("Cloudflare").contains("Turnstile").contains("Brevo").contains("en France")
                // Droits et réclamation.
                .contains("CNIL").contains("href=\"https://www.cnil.fr/fr/plaintes\"")
                .doesNotContain("en cours de rédaction");
    }

    @Test
    void the_durations_are_those_applied_by_the_purge() throws Exception {
        assertThat(notice())
                .contains("un mois après la fin de la saison")
                .contains("au plus 3 ans sans participation")
                .contains("au bout de 7 jours")
                .contains("au plus 6 mois")
                .contains("au plus 30 jours");
        assertThat(RetentionPolicy.AFTER_SEASON_WITHOUT_CONSENT.getMonths()).isEqualTo(1);
        assertThat(RetentionPolicy.WITHOUT_PARTICIPATION_WITH_CONSENT.getYears()).isEqualTo(3);
        assertThat(RetentionPolicy.INCOMPLETE_ACCOUNT.toDays()).isEqualTo(7);
        assertThat(RetentionPolicy.TECHNICAL_DATA.getMonths()).isEqualTo(6);
    }

    @Test
    void the_controller_and_the_contact_come_from_the_season_settings() throws Exception {
        jdbc.update("UPDATE season SET organizer = ?, coordinators_contact = ?", ORGANIZER, "01 99 00 12 34");

        assertThat(notice()).contains(ORGANIZER).contains("href=\"tel:0199001234\"").contains("01 99 00 12 34")
                .doesNotContain("pas encore renseigné");
    }

    @Test
    void without_settings_the_notice_says_so_soberly() throws Exception {
        assertThat(notice()).contains("l’organisateur de l’accueil, dont le nom n’est pas encore renseigné")
                .contains("Contactez un coordinateur.").doesNotContain(ORGANIZER).doesNotContain("href=\"tel:");
    }

    @Test
    void the_notice_is_linked_from_the_first_profile_my_account_and_every_footer() throws Exception {
        var volunteers = new TestVolunteers(mockMvc, sender, coordinators, jdbc);
        var newcomer = volunteers.signIn(Browser.uniqueEmail(), false);
        assertThat(newcomer.browser().get("/profil").getContentAsString())
                .contains("<a href=\"" + NOTICE + "\" target=\"_blank\" rel=\"noopener\">");

        var anne = volunteers.volunteer();
        assertThat(anne.browser().get("/mon-compte").getContentAsString()).contains("<a href=\"" + NOTICE + "\">");

        for (var page : new String[] {"/", "/planning", "/connexion", NOTICE}) {
            assertThat(new Browser(mockMvc).get(page).getContentAsString()).as(page)
                    .contains("<footer class=\"pied\">").contains("href=\"" + NOTICE + "\"");
        }
    }

    @Test
    void the_former_provisional_address_is_gone() throws Exception {
        assertThat(new Browser(mockMvc).get("/notice").getStatus()).isEqualTo(403);
    }
}
