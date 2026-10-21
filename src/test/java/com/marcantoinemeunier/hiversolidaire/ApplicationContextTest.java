package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationContextTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Test
    void context_starts_on_real_postgresql() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    @Test
    void flyway_applies_baseline_migration() {
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }
}
