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
            assertThat(INLINE_SCRIPT.matcher(html).find()).as(name + " : script sans th:src").isFalse();
            assertThat(EVENT_HANDLER.matcher(html).find()).as(name + " : gestionnaire d'événement inline").isFalse();
        }
    }

    @Test
    void every_page_uses_the_common_head_and_header() throws IOException {
        var templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");

        for (var template : templates) {
            var name = template.getFilename();
            if ("fragments.html".equals(name)) {
                continue;
            }
            var html = template.getContentAsString(StandardCharsets.UTF_8);

            // Le titre de la page est dans l'en-tête commun (fragment entete), jamais en dur.
            assertThat(html).as(name).contains("~{fragments :: head(").contains("~{fragments :: entete(")
                    .doesNotContain("<h1");
        }
    }
}
