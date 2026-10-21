package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
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
        var csp = mockMvc.perform(get("/")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
    }

    @Test
    void hsts_is_left_to_cloudflare() throws Exception {
        var response = mockMvc.perform(get("/")).andReturn().getResponse();
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }
}
