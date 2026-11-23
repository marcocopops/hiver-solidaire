package com.marcantoinemeunier.hiversolidaire.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.marcantoinemeunier.hiversolidaire.TestClockAndMailConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base arrêtée : la page de santé répond 503, vite (avant le délai de la sonde externe), et toujours sans détail.
 * Base et contexte propres à ce test, puisqu'il arrête PostgreSQL.
 */
@SpringBootTest(properties = "app.health.cache-ttl=PT0S")
@AutoConfigureMockMvc
@Import({PublicHealthDatabaseDownTest.OwnDatabase.class, TestClockAndMailConfiguration.class})
@DirtiesContext
class PublicHealthDatabaseDownTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class OwnDatabase {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgreSQLContainer postgres;

    @Test
    void answers_503_without_detail_when_the_database_is_down() throws Exception {
        assertThat(mockMvc.perform(get("/sante")).andReturn().getResponse().getStatus()).isEqualTo(200);

        postgres.stop();

        long start = System.nanoTime();
        var response = mockMvc.perform(get("/sante")).andReturn().getResponse();
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
        // Sous le délai de la sonde externe (30 s chez UptimeRobot) : délai du pool réduit à 5 s.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(15));
    }
}
