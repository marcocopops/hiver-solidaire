package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Aucun gabarit ne porte de style ni de script inline (la CSP les bloquerait, cahier des charges §8), ni de contenu
 * non échappé : tout passe par des classes, des fichiers statiques et {@code th:text}.
 */
class TemplatesHaveNoInlineCodeTest {

    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bth:src=)[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final String TURNSTILE_SCRIPT =
            "<script src=\"https://challenges.cloudflare.com/turnstile/v0/api.js\" async defer></script>";
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\s(?:th:)?on[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    @Test
    void templates_have_no_inline_style_script_or_unescaped_text() throws IOException {
        var templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");
        assertThat(templates).isNotEmpty();

        for (var template : templates) {
            var html = template.getContentAsString(StandardCharsets.UTF_8);
            var name = template.getFilename();

            assertThat(html).as(name)
                    .doesNotContain("style=")
                    .doesNotContain("<style")
                    .doesNotContain("th:utext")
                    .doesNotContain("javascript:");
            // Seule exception (ADR-0013) : le script de Turnstile, servi par Cloudflare, sur la page de demande de lien.
            var scripts = "connexion.html".equals(name) ? html.replace(TURNSTILE_SCRIPT, "") : html;
            assertThat(INLINE_SCRIPT.matcher(scripts).find()).as(name + " : script sans th:src").isFalse();
            assertThat(EVENT_HANDLER.matcher(html).find()).as(name + " : gestionnaire d'événement inline").isFalse();
        }
    }

    /** Aucune ressource externe, hormis le script de Turnstile sur la page de demande de lien (ADR-0013). */
    @Test
    void no_template_loads_an_external_resource() throws IOException {
        var templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");
        var external = Pattern.compile("\\bsrc=\"(?:https?:)?//", Pattern.CASE_INSENSITIVE);

        for (var template : templates) {
            var html = template.getContentAsString(StandardCharsets.UTF_8);
            var name = template.getFilename();
            if ("connexion.html".equals(name)) {
                assertThat(html).as(name).containsOnlyOnce(TURNSTILE_SCRIPT);
                html = html.replace(TURNSTILE_SCRIPT, "");
            }
            assertThat(external.matcher(html).find()).as(name + " : ressource externe").isFalse();
            assertThat(html).as(name).doesNotContain("<link href=\"http").doesNotContain("<iframe");
        }
    }

    @Test
    void every_page_uses_the_common_head_and_header() throws IOException {
        var templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");

        for (var template : templates) {
            var name = template.getFilename();
            // Les e-mails (S5a) n'ont ni l'en-tête ni la feuille de style du site : voir le test suivant.
            if ("fragments.html".equals(name) || isMail(template)) {
                continue;
            }
            var html = template.getContentAsString(StandardCharsets.UTF_8);

            // Le titre de la page est dans l'en-tête commun (fragment entete), jamais en dur.
            assertThat(html).as(name).contains("~{fragments :: head(").contains("~{fragments :: entete(")
                    .doesNotContain("<h1");
            // Pied de page avec le lien vers la notice d'information (§7), sauf sur la page imprimable (plan B).
            if (!"impression.html".equals(name)) {
                assertThat(html).as(name).contains("<footer th:replace=\"~{fragments :: pied}\"></footer>");
            }
        }
    }

    /**
     * E-mails (S5a, cahier des charges §6) : HTML simple, sans image, ni ressource externe, ni script. Aucun pixel de
     * suivi : rien ne se charge à l'ouverture. Les liens sont des {@code th:href} vers l'appli, jamais en dur.
     */
    @Test
    void mail_templates_load_nothing_and_track_nothing() throws IOException {
        var templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/mail/*.html");
        assertThat(templates).isNotEmpty();

        for (var template : templates) {
            var html = template.getContentAsString(StandardCharsets.UTF_8);

            assertThat(html).as(template.getFilename())
                    .doesNotContain("<img")
                    .doesNotContain("<link")
                    .doesNotContain("<script")
                    .doesNotContain("<iframe")
                    .doesNotContain("src=")
                    .doesNotContain("href=\"http")
                    .doesNotContain("~{fragments");
        }
    }

    private static boolean isMail(org.springframework.core.io.Resource template) throws IOException {
        return template.getURL().getPath().contains("/templates/mail/");
    }
}
