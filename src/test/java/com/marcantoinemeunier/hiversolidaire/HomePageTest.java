package com.marcantoinemeunier.hiversolidaire;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HomePageTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void home_page_is_public_and_shows_opening_date() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hiver Solidaire")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Inscriptions à partir du 14 décembre 2026")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"fr\"")));
    }

    @Test
    void home_page_has_no_inline_script_or_style() throws Exception {
        var body = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("<script")
                .doesNotContain("<style")
                .doesNotContain("style=");
    }
}
