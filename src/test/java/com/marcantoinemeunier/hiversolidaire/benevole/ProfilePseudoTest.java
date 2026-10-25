package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThat;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.RecordingMagicLinkSender;
import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.HtmlUtils;

/** Pseudo public au formulaire de première connexion (cahier des charges §6 étape 4, §7). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class ProfilePseudoTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    @Autowired
    JdbcTemplate jdbc;

    /** Bénévole connecté, profil encore vide, sur la page du formulaire. */
    private Browser onProfileForm() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();
        browser.get("/login/ott?token=" + token);
        browser.post("/login/ott", "token", token);
        browser.get("/profil");
        return browser;
    }

    private MockHttpServletResponse submit(Browser browser, String lastName, String firstName, String pseudo)
            throws Exception {
        return browser.post("/profil", "lastName", lastName, "firstName", firstName, "pseudo", pseudo,
                "phone", "06 00 00 00 01");
    }

    private String uniqueLastName() {
        // Lettres seulement : la règle des noms refuse les chiffres.
        return "Nom" + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
    }

    private int profilesWithPseudo(String pseudo) {
        return jdbc.queryForObject("SELECT count(*) FROM volunteer WHERE lower(pseudo) = lower(?)", Integer.class,
                pseudo);
    }

    @Test
    void the_pseudo_field_follows_the_names_and_says_it_is_public() throws Exception {
        var body = onProfileForm().get("/profil").getContentAsString();

        assertThat(body.indexOf("id=\"lastName\"")).isLessThan(body.indexOf("id=\"pseudo\""));
        assertThat(body.indexOf("id=\"firstName\"")).isLessThan(body.indexOf("id=\"pseudo\""));
        assertThat(body.indexOf("id=\"pseudo\"")).isLessThan(body.indexOf("id=\"phone\""));
        assertThat(body).contains("Votre pseudo est public").contains("autre chose que votre prénom")
                .contains("Seul votre pseudo est public").doesNotContain("ces informations ne sont visibles")
                .contains("<script src=\"/js/profil.js\" defer></script>");
    }

    @Test
    void an_empty_pseudo_gets_the_suggestion_for_confirmation_without_saving() throws Exception {
        var browser = onProfileForm();
        var lastName = uniqueLastName();

        var response = submit(browser, lastName, "Zoé", "");

        assertThat(response.getStatus()).isEqualTo(200);
        var expected = Pseudo.suggest("Zoé", lastName);
        assertThat(response.getContentAsString()).contains("value=\"" + HtmlUtils.htmlEscape(expected) + "\"")
                .contains("Voici le pseudo proposé");
        assertThat(browser.get("/mes-inscriptions").getRedirectedUrl()).as("profil non enregistré").isEqualTo("/profil");
    }

    @Test
    void the_suggestion_skips_pseudos_already_taken() throws Exception {
        var lastName = uniqueLastName();
        var taken = Pseudo.suggest("Zoé", lastName);
        assertThat(submit(onProfileForm(), "Autre", "Zoé", taken).getRedirectedUrl()).isEqualTo("/mes-inscriptions");

        var response = submit(onProfileForm(), lastName, "Zoé", "");

        assertThat(response.getContentAsString()).contains("value=\"" + HtmlUtils.htmlEscape(Pseudo.variant(taken, 2)) + "\"");
    }

    @Test
    void a_valid_pseudo_is_saved_normalized() throws Exception {
        var browser = onProfileForm();
        var pseudo = "Zoé" + UUID.randomUUID().toString().substring(0, 8).replaceAll("[^a-f0-9]", "x");

        var response = submit(browser, "Martin", "Zoé", "  " + pseudo + " ");

        assertThat(response.getRedirectedUrl()).isEqualTo("/mes-inscriptions");
        assertThat(profilesWithPseudo(pseudo.replace("é", "é"))).isOne();
    }

    @Test
    void a_pseudo_taken_with_another_case_is_refused_with_a_variant() throws Exception {
        var pseudo = Browser.uniquePseudo();
        submit(onProfileForm(), "Martin", "Anne", pseudo);

        var response = submit(onProfileForm(), "Martin", "Anne", pseudo.toLowerCase());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Ce pseudo est déjà pris")
                .contains("value=\"" + Pseudo.variant(pseudo.toLowerCase(), 2) + "\"");
        assertThat(profilesWithPseudo(pseudo)).isOne();
    }

    @Test
    void invalid_pseudos_are_refused_and_redisplayed_escaped() throws Exception {
        var browser = onProfileForm();

        var response = submit(browser, "Martin", "Anne", "<script>alert(1)</script>");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("Le pseudo doit faire de 3 à 20 caractères")
                .doesNotContain("<script>alert").contains("&lt;script&gt;");
        assertThat(browser.get("/mes-inscriptions").getRedirectedUrl()).isEqualTo("/profil");
    }

    @Test
    void two_volunteers_submitting_the_same_pseudo_at_once_only_one_gets_it() throws Exception {
        var pseudo = Browser.uniquePseudo();
        var first = onProfileForm();
        var second = onProfileForm();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var attempts = List.<Callable<MockHttpServletResponse>>of(
                    () -> {
                        start.await();
                        return submit(first, "Martin", "Anne", pseudo);
                    },
                    () -> {
                        start.await();
                        return submit(second, "Durand", "Anne", pseudo.toUpperCase());
                    });
            var futures = new ArrayList<Future<MockHttpServletResponse>>();
            for (var attempt : attempts) {
                futures.add(executor.submit(attempt));
            }
            start.countDown();
            var saved = 0;
            var refused = 0;
            for (var future : futures) {
                var response = future.get();
                if ("/mes-inscriptions".equals(response.getRedirectedUrl())) {
                    saved++;
                } else if (response.getContentAsString().contains("Ce pseudo est déjà pris")) {
                    refused++;
                }
            }
            assertThat(saved).isOne();
            assertThat(refused).isOne();
            assertThat(profilesWithPseudo(pseudo)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }
}
