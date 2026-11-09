package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Formulaires des pages bénévoles (cahier des charges §8, WCAG 2.2 AA) : chaque champ a son libellé, chaque champ
 * invalide est relié à son message d'erreur ({@code aria-describedby}), chaque message d'erreur à un champ ; chaque
 * page porte l'en-tête commun avec un seul titre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class FormAccessibilityTest {

    /** Mardi 12 janvier 2027 : journée ouverte aux inscriptions pendant la saison de test. */
    private static final LocalDate DAY = LocalDate.of(2027, 1, 12);

    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>");
    private static final Pattern ERROR = Pattern.compile("<p class=\"erreur\"[^>]*>");
    private static final Pattern DESCRIBED_BY = Pattern.compile("aria-describedby=\"([^\"]*)\"");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordingMagicLinkSender sender;

    /** Bénévole connecté dont le profil reste à remplir. */
    private Browser signedInWithoutProfile() throws Exception {
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

    private Browser signedIn() throws Exception {
        var browser = signedInWithoutProfile();
        browser.post("/profil", "pseudo", Browser.uniquePseudo(), "lastName", "Martin", "firstName", "Anne",
                "phone", "0600000001");
        return browser;
    }

    private static String attribute(String tag, String name) {
        var matcher = Pattern.compile("\\s" + name + "=\"([^\"]*)\"").matcher(tag);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Set<String> describedBy(String tag) {
        var value = attribute(tag, "aria-describedby");
        return value == null ? Set.of() : new HashSet<>(Arrays.asList(value.trim().split("\\s+")));
    }

    /** Vérifie les liens entre libellés, champs, aides et erreurs d'une page de formulaire. */
    private static void assertAccessibleForm(String html, int expectedErrors) {
        var inputs = INPUT.matcher(html).results().map(match -> match.group()).toList();
        var visibleInputs = inputs.stream().filter(tag -> !"hidden".equals(attribute(tag, "type"))).toList();
        assertThat(visibleInputs).isNotEmpty();

        for (var input : visibleInputs) {
            var id = attribute(input, "id");
            assertThat(id).as("id de " + input).isNotNull();
            assertThat(html).as("libellé de " + id).contains("<label for=\"" + id + "\"");
        }

        // Toute référence aria-describedby pointe vers un élément de la page.
        var referenced = new HashSet<String>();
        DESCRIBED_BY.matcher(html).results().forEach(match -> referenced.addAll(
                Arrays.asList(match.group(1).trim().split("\\s+"))));
        for (var id : referenced) {
            assertThat(html).as("cible de aria-describedby").contains(" id=\"" + id + "\"");
        }

        // Chaque message d'erreur a un id, relié à au moins un champ invalide.
        List<String> errors = ERROR.matcher(html).results().map(match -> match.group()).toList();
        assertThat(errors).hasSize(expectedErrors);
        for (var error : errors) {
            var id = attribute(error, "id");
            assertThat(id).as("id de " + error).isNotNull();
            assertThat(visibleInputs.stream().filter(input -> describedBy(input).contains(id)))
                    .as("champ relié à " + id)
                    .isNotEmpty()
                    .allMatch(input -> "true".equals(attribute(input, "aria-invalid")));
        }

        // Chaque champ invalide annonce au moins un message d'erreur.
        var errorIds = errors.stream().map(error -> attribute(error, "id")).toList();
        for (var input : visibleInputs) {
            if ("true".equals(attribute(input, "aria-invalid"))) {
                assertThat(describedBy(input)).as("erreur de " + attribute(input, "id"))
                        .containsAnyElementsOf(errorIds);
            }
        }
    }

    private static void assertCommonHeader(String html) {
        assertThat(html).contains("<header class=\"entete\">");
        assertThat(html.split("<h1", -1)).as("un seul titre").hasSize(2);
        assertThat(html.indexOf("<h1")).isBetween(html.indexOf("<header"), html.indexOf("</header>"));
    }

    @Test
    void the_login_form_ties_its_error_to_the_email_field() throws Exception {
        var browser = new Browser(mockMvc);
        assertAccessibleForm(browser.get("/connexion").getContentAsString(), 0);

        var html = browser.post("/connexion", "email", "pas-une-adresse").getContentAsString();

        assertAccessibleForm(html, 1);
        assertCommonHeader(html);
    }

    @Test
    void the_profile_form_ties_each_error_to_its_field() throws Exception {
        var browser = signedInWithoutProfile();
        var page = browser.get("/profil").getContentAsString();
        assertAccessibleForm(page, 0);
        assertCommonHeader(page);

        var html = browser.post("/profil", "lastName", "", "firstName", "", "pseudo", "x",
                "phoneCountryCode", "", "phone", "").getContentAsString();

        // Nom, prénom, pseudo, indicatif, numéro.
        assertAccessibleForm(html, 5);
        assertThat(html).contains("aria-describedby=\"pseudo-hint pseudo-error\"");
    }

    @Test
    void the_account_forms_tie_their_errors_to_their_fields() throws Exception {
        var browser = signedIn();
        var page = browser.get("/mon-compte").getContentAsString();
        assertAccessibleForm(page, 0);
        assertCommonHeader(page);

        assertAccessibleForm(browser.post("/mon-compte/pseudo", "pseudo", "x").getContentAsString(), 1);
        assertAccessibleForm(browser.post("/mon-compte/telephone", "phoneCountryCode", "abc", "phone", "abc")
                .getContentAsString(), 2);
    }

    @Test
    void the_registration_pages_carry_the_header_and_labelled_choices() throws Exception {
        var choose = new Browser(mockMvc).get("/inscription?jour=" + DAY).getContentAsString();
        assertAccessibleForm(choose, 0);
        assertCommonHeader(choose);

        var confirm = signedIn().get("/inscription/confirmer?jour=" + DAY + "&choix=NIGHT_AND_DINNER")
                .getContentAsString();
        assertCommonHeader(confirm);
    }

    @Test
    void the_login_pages_carry_the_header_without_a_sign_in_button() throws Exception {
        var browser = new Browser(mockMvc);
        var email = Browser.uniqueEmail();
        browser.get("/connexion");
        var sent = browser.post("/connexion", "email", email);
        var token = sender.lastSentTo(email).orElseThrow().token();

        for (var html : new String[] {
            browser.get("/connexion").getContentAsString(),
            browser.get(sent.getRedirectedUrl()).getContentAsString(),
            browser.get("/login/ott?token=" + token).getContentAsString()}) {
            assertCommonHeader(html);
            // On est déjà en train de se connecter : pas d'entrée « Me connecter » dans le menu de l'en-tête.
            var menu = html.substring(html.indexOf("<details class=\"menu\">"), html.indexOf("</details>"));
            assertThat(menu).contains("href=\"/planning\"").doesNotContain("Me connecter")
                    .doesNotContain("href=\"/connexion\"");
        }
    }

    @Test
    void the_volunteer_navigation_is_labelled_and_signs_out_with_a_post() throws Exception {
        var html = signedIn().get("/mon-compte").getContentAsString();

        var header = html.substring(html.indexOf("<header"), html.indexOf("</header>"));
        assertThat(header).contains("<nav class=\"menu__liste\" aria-label=\"Menu principal\">")
                .contains("href=\"/planning\"").contains("href=\"/mes-inscriptions\"")
                .contains("action=\"/deconnexion\" method=\"post\"");
    }
}
