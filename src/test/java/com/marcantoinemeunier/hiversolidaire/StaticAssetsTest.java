package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Police, icônes et feuille de style servies par l'appli elle-même (pas de CDN, CSP {@code default-src 'self'}),
 * lisibles sans être connecté : un visiteur voit le planning avec la même mise en forme.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaticAssetsTest {

    private static final Pattern CSS_URL = Pattern.compile("url\\(\"?(/[^\")]+)\"?\\)");

    @Autowired
    MockMvc mockMvc;

    @Test
    void fonts_are_public_and_served_as_woff2() throws Exception {
        for (var weight : new String[] {"400", "700"}) {
            for (var subset : new String[] {"latin", "latin-ext"}) {
                var url = "/fonts/atkinson-hyperlegible-" + subset + "-" + weight + "-normal.woff2";
                var response = mockMvc.perform(get(url)).andReturn().getResponse();

                assertThat(response.getStatus()).as(url).isEqualTo(200);
                assertThat(response.getContentType()).as(url).isEqualTo("font/woff2");
            }
        }
    }

    @Test
    void the_font_license_ships_with_the_fonts() throws Exception {
        var response = mockMvc.perform(get("/fonts/OFL.txt")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("SIL Open Font License");
    }

    @Test
    void the_icon_sprite_is_public_svg() throws Exception {
        var response = mockMvc.perform(get("/images/icones.svg")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/svg+xml");
        assertThat(response.getContentAsString()).doesNotContain("<script").doesNotContain("<style");
    }

    @Test
    void every_file_referenced_by_the_stylesheet_exists() throws Exception {
        var css = mockMvc.perform(get("/css/app.css")).andReturn().getResponse().getContentAsString();
        var urls = CSS_URL.matcher(css).results().map(match -> match.group(1)).toList();

        assertThat(urls).hasSize(4);
        for (var url : urls) {
            assertThat(mockMvc.perform(get(url)).andReturn().getResponse().getStatus()).as(url).isEqualTo(200);
        }
    }
}
