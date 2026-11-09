package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Page d'erreur rendue par le vrai serveur (MockMvc ne passe pas par la page d'erreur) : en-tête commun, message
 * en français, aucun détail technique (cahier des charges §8), en-têtes de sécurité conservés.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ErrorPageTest {

    @Value("${local.server.port}")
    int port;

    record Page(int status, String body, HttpHeaders headers) {
    }

    private Page get(String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path).accept(MediaType.TEXT_HTML)
                .exchange((request, response) -> new Page(response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                        response.getHeaders()));
    }

    private static void assertNoTechnicalDetail(String body) {
        assertThat(body).doesNotContain("Whitelabel").doesNotContain("Exception").doesNotContain("trace")
                .doesNotContain("com.marcantoinemeunier").doesNotContain("Spring");
    }

    @Test
    void an_unknown_day_shows_the_not_found_page() {
        var page = get("/inscription?jour=2099-01-01");

        assertThat(page.status()).isEqualTo(404);
        assertThat(page.body()).contains("<header class=\"entete\">").contains("<h1>Page introuvable</h1>")
                .contains("href=\"/planning\"").contains("<link rel=\"stylesheet\" href=\"/css/app.css\">");
        assertNoTechnicalDetail(page.body());
    }

    @Test
    void an_expired_form_shows_the_access_denied_page() {
        // Formulaire envoyé sans jeton CSRF valide (session expirée, par exemple) : 403 par le gestionnaire de refus.
        var page = RestClient.create("http://localhost:" + port).post().uri("/connexion")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.TEXT_HTML)
                .body("email=anne%40example.org")
                .exchange((request, response) -> new Page(response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                        response.getHeaders()));

        assertThat(page.status()).isEqualTo(403);
        assertThat(page.body()).contains("<h1>Accès refusé</h1>").contains("href=\"/connexion\"");
        assertNoTechnicalDetail(page.body());
    }

    @Test
    void the_error_page_keeps_the_security_headers() {
        var page = get("/inscription?jour=2099-01-01");

        assertThat(page.headers().getFirst("Content-Security-Policy")).startsWith("default-src 'self'");
        assertThat(page.headers().getFirst("X-Robots-Tag")).isEqualTo("noindex, nofollow");
        assertThat(page.body()).doesNotContain("style=").doesNotContain("<script");
    }
}
