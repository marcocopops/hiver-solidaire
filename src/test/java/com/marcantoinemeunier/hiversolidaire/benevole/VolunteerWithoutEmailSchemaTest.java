package com.marcantoinemeunier.hiversolidaire.benevole;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marcantoinemeunier.hiversolidaire.Browser;
import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** V11 : un compte actif sans e-mail n'existe qu'avec un profil complet (bénévole inscrit par téléphone). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VolunteerWithoutEmailSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void an_active_volunteer_without_email_needs_a_complete_profile() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO volunteer (id) VALUES (?)", UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO volunteer (id, last_name, first_name) VALUES (?, 'M', 'A')",
                UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        assertThatCode(() -> jdbc.update("""
                INSERT INTO volunteer (id, last_name, first_name, phone, pseudo, profile_completed_at)
                VALUES (?, 'Martin', 'Anne', '+33600000001', ?, now())
                """, UUID.randomUUID(), Browser.uniquePseudo())).doesNotThrowAnyException();
    }

    @Test
    void a_deleted_account_stays_anonymous() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO volunteer (id, last_name, first_name, phone, pseudo, profile_completed_at, deleted_at)
                VALUES (?, 'Martin', 'Anne', '+33600000001', ?, now(), now())
                """, UUID.randomUUID(), Browser.uniquePseudo())).isInstanceOf(DataAccessException.class);
    }
}
