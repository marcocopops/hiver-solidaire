package com.marcantoinemeunier.hiversolidaire.planning;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La migration V13 retire le nom de la saison, l'adresse du lieu et les consignes, et renomme le téléphone des
 * coordinateurs en contact, sur une base dont la saison a tous ces réglages : le téléphone est conservé.
 */
class SeasonSimplificationMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final String PHONE = "01 99 00 12 34";
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void migrateToV12AndFill() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway("12").migrate();
        jdbc.update("""
                UPDATE season SET venue_address = '1 rue de l''Exemple, 00000 Ville-Fictive',
                    instructions = 'Sonner deux fois.', coordinators_phone = ?
                """, PHONE);
        flyway(null).migrate();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static int column(String name) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_name = 'season' AND column_name = ?
                """, Integer.class, name);
    }

    @Test
    void the_name_the_venue_and_the_instructions_are_gone() {
        assertThat(column("label")).isZero();
        assertThat(column("venue_address")).isZero();
        assertThat(column("instructions")).isZero();
        assertThat(column("coordinators_phone")).isZero();
    }

    @Test
    void the_phone_is_kept_as_the_contact() {
        assertThat(jdbc.queryForObject("SELECT coordinators_contact FROM season", String.class)).isEqualTo(PHONE);
    }

    @Test
    void the_contact_fits_an_email_address() {
        assertThat(jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_name = 'season' AND column_name = 'coordinators_contact'
                """, Integer.class)).isEqualTo(254);
    }

    @Test
    void the_season_and_its_evenings_are_kept() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM season", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evening", Integer.class)).isEqualTo(76);
    }
}
