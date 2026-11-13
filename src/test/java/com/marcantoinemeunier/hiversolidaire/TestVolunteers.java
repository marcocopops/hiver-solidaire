package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bénévoles et coordinateurs connectés pour les tests d'intégration web : connexion par le vrai parcours du lien
 * magique, puis formulaire de première connexion. Données factices uniquement ({@code @example.org}, numéros fictifs).
 */
public final class TestVolunteers {

    /** @param landing page où la connexion a mené ({@code /profil}, {@code /admin}…) */
    public record SignedIn(Browser browser, UUID id, String email, String pseudo, String landing) {
    }

    private final MockMvc mockMvc;
    private final RecordingMagicLinkSender sender;
    private final TestCoordinatorEmails coordinators;
    private final JdbcTemplate jdbc;

    public TestVolunteers(MockMvc mockMvc, RecordingMagicLinkSender sender, TestCoordinatorEmails coordinators,
            JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.sender = sender;
        this.coordinators = coordinators;
        this.jdbc = jdbc;
    }

    /** Bénévole connecté, profil rempli (nom « Martin », prénom « Anne », téléphone fictif). */
    public SignedIn volunteer() throws Exception {
        return signIn(Browser.uniqueEmail(), true);
    }

    /** Coordinateur connecté : son adresse est ajoutée à la liste avant la connexion. */
    public SignedIn coordinator() throws Exception {
        var email = "coordinateur-" + UUID.randomUUID() + "@example.org";
        coordinators.add(email);
        return signIn(email, true);
    }

    /** Connexion par lien magique, sur un nouvel appareil. */
    public SignedIn signIn(String email, boolean completeProfile) throws Exception {
        var browser = new Browser(mockMvc);
        browser.get("/connexion");
        browser.requestLink(email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        var login = browser.post("/login/ott", "token", token);
        assertThat(login.getStatus()).isEqualTo(302);
        var id = jdbc.queryForObject("SELECT id FROM volunteer WHERE email = ?", UUID.class, email);
        String pseudo = jdbc.queryForObject("SELECT pseudo FROM volunteer WHERE id = ?", String.class, id);
        if (completeProfile && pseudo == null) {
            pseudo = Browser.uniquePseudo();
            browser.get("/profil");
            browser.post("/profil", "pseudo", pseudo, "lastName", "Martin", "firstName", "Anne", "phone",
                    "0600000001");
        }
        return new SignedIn(browser, id, email, pseudo, login.getRedirectedUrl());
    }

    /** Inscription en ligne, par le bénévole lui-même, depuis la journée de l'activité. */
    public static void register(Browser browser, LocalDate eveningDate, String choice) throws Exception {
        var day = "BREAKFAST".equals(choice) ? eveningDate.plusDays(1) : eveningDate;
        browser.get("/inscription/confirmer?jour=" + day + "&choix=" + choice);
        assertThat(browser.post("/inscription", "jour", day.toString(), "choix", choice).getRedirectedUrl())
                .isEqualTo("/inscription/confirmee");
    }
}
