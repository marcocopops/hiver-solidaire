package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessControlTest {

    @Autowired
    MockMvc mockMvc;

    private MockHttpServletResponse call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void non_public_url_is_denied_to_anonymous() throws Exception {
        var response = call(get("/admin"));
        assertThat(response.getStatus()).isIn(401, 403);
    }

    @Test
    void unknown_url_is_denied_by_default() throws Exception {
        assertThat(call(get("/anything-else")).getStatus()).isIn(401, 403);
    }

    @Test
    void actuator_is_not_served_on_public_port() throws Exception {
        assertThat(call(get("/actuator/health")).getStatus()).isIn(401, 403, 404);
    }

    @Test
    void no_http_basic_challenge() throws Exception {
        assertThat(call(get("/admin")).getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    void no_default_login_form() throws Exception {
        var response = call(get("/login"));
        assertThat(response.getStatus()).isIn(401, 403, 404);
        assertThat(response.getContentAsString()).doesNotContain("Please sign in");
    }

    @Test
    void post_without_csrf_token_is_rejected() throws Exception {
        assertThat(call(post("/")).getStatus()).isEqualTo(403);
    }

    @Test
    void planning_is_public() throws Exception {
        assertThat(call(get("/planning")).getStatus()).isEqualTo(200);
    }

    @Test
    void planning_is_read_only() throws Exception {
        assertThat(call(post("/planning")).getStatus()).isEqualTo(403);
        assertThat(call(post("/planning").with(csrf())).getStatus()).isIn(401, 403, 405);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/planning/00000000-0000-0000-0000-000000000000",
        "/planning/inscrits",
        "/planning.csv",
        "/soirees/00000000-0000-0000-0000-000000000000",
        "/inscriptions",
        "/benevoles",
        "/api/planning",
        "/admin/soirees"
    })
    void no_route_exposes_registrations_or_contact_details(String url) throws Exception {
        assertThat(call(get(url)).getStatus()).isIn(401, 403, 404);
    }
}
