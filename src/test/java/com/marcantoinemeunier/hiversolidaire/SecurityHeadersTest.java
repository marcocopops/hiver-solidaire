package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class SecurityHeadersTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void home_page_carries_security_headers() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/")).andReturn().getResponse();

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'; object-src 'none'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy"))
                .contains("camera=()", "microphone=()", "geolocation=()");
    }

    @Test
    void csp_does_not_allow_unsafe_inline() throws Exception {
        for (var url : new String[] {"/", "/connexion", "/planning"}) {
            assertThat(csp(url)).as(url).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        }
    }

    /**
     * Exception à la règle « pas de CDN » (ADR-0013) : le script de Turnstile et son cadre viennent de Cloudflare, sur la
     * page de demande de lien seulement, et Cloudflare n'est autorisé nulle part ailleurs.
     */
    @Test
    void the_login_page_allows_turnstile_in_script_src_and_frame_src_only() throws Exception {
        var directives = directives(csp("/connexion"));

        assertThat(directives).containsEntry("script-src", "'self' https://challenges.cloudflare.com")
                .containsEntry("frame-src", "https://challenges.cloudflare.com")
                .containsEntry("default-src", "'self'")
                .containsEntry("frame-ancestors", "'none'")
                .containsEntry("form-action", "'self'")
                .containsEntry("base-uri", "'self'")
                .containsEntry("object-src", "'none'")
                .hasSize(7);
        directives.forEach((name, value) -> {
            if (!name.equals("script-src") && !name.equals("frame-src")) {
                assertThat(value).as(name).doesNotContain("cloudflare");
            }
        });
    }

    @Test
    void the_login_post_keeps_the_turnstile_policy() throws Exception {
        // Le formulaire réaffiché après un refus porte de nouveau le widget.
        var response = mockMvc.perform(post("/connexion").with(csrf()).param("email", "pas-une-adresse"))
                .andReturn().getResponse();

        assertThat(response.getHeader("Content-Security-Policy")).contains("https://challenges.cloudflare.com");
    }

    @Test
    void no_other_page_allows_cloudflare() throws Exception {
        for (var url : new String[] {"/", "/planning", "/connexion/envoye", "/donnees-personnelles", "/mon-compte",
            "/admin", "/css/app.css", "/connexion-autre"}) {
            assertThat(csp(url)).as(url).isEqualTo(
                    "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'; object-src 'none'");
        }
    }

    private String csp(String url) throws Exception {
        return mockMvc.perform(get(url)).andReturn().getResponse().getHeader("Content-Security-Policy");
    }

    private static Map<String, String> directives(String csp) {
        var directives = new LinkedHashMap<String, String>();
        for (var directive : csp.split(";")) {
            var parts = directive.strip().split("\\s+", 2);
            assertThat(directives.put(parts[0], parts.length > 1 ? parts[1] : "")).as("directive en double").isNull();
        }
        return directives;
    }

    @Test
    void hsts_is_left_to_cloudflare() throws Exception {
        var response = mockMvc.perform(get("/")).andReturn().getResponse();
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void no_page_is_indexed_by_search_engines() throws Exception {
        // Le planning affiche des pseudos ; aucune page de l'appli n'a besoin d'être indexée.
        for (var url : new String[] {"/", "/planning", "/connexion", "/donnees-personnelles", "/css/app.css"}) {
            var response = mockMvc.perform(get(url)).andReturn().getResponse();
            assertThat(response.getHeader("X-Robots-Tag")).as(url).isEqualTo("noindex, nofollow");
        }
    }
}
