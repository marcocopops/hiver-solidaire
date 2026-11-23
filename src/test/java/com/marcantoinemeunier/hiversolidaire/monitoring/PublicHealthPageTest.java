package com.marcantoinemeunier.hiversolidaire.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Page de santé publique pour la sonde externe (S6) : servie sans connexion, elle ne dit rien d'autre que son code de
 * statut. Le cas « base arrêtée » est dans {@link PublicHealthDatabaseDownTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockAndMailConfiguration.class})
class PublicHealthPageTest {

    @Autowired
    MockMvc mockMvc;

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void answers_200_without_login_when_the_database_answers() throws Exception {
        assertThat(call(get("/sante")).getStatus()).isEqualTo(200);
    }

    @Test
    void head_is_accepted_for_probes_that_use_it() throws Exception {
        assertThat(call(head("/sante")).getStatus()).isEqualTo(200);
    }

    @Test
    void gives_no_detail() throws Exception {
        var response = call(get("/sante"));

        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeaderNames()).doesNotContain("Server", "X-Powered-By", "X-Application-Context");
    }

    @Test
    void is_never_cached_and_sets_no_cookie() throws Exception {
        var response = call(get("/sante"));

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void carries_the_security_headers() throws Exception {
        var response = call(get("/sante"));

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'; object-src 'none'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");
    }

    @Test
    void other_methods_are_refused() throws Exception {
        assertThat(call(post("/sante").with(csrf())).getStatus()).isIn(403, 405);
        assertThat(call(post("/sante")).getStatus()).isEqualTo(403);
    }

    @Test
    void sub_paths_are_not_public() throws Exception {
        assertThat(call(get("/sante/details")).getStatus()).isIn(403, 404);
    }
}
