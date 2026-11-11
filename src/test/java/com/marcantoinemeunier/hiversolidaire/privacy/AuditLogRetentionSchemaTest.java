package com.marcantoinemeunier.hiversolidaire.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Journal d'audit en ajout seul (V10), avec une seule exception contrôlée par la base elle-même (V19, ADR-0014) : une
 * ligne de plus de six mois peut être supprimée par la purge. Une modification, ou la suppression d'une ligne récente,
 * reste refusée, y compris par l'appli.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditLogRetentionSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    private UUID entry(String age) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_log (id, occurred_at, coordinator_id, action) VALUES (?, now() - ?::interval, ?, "
                + "'VOLUNTEER_REVIEWED')", id, age, UUID.randomUUID());
        return id;
    }

    @Test
    void an_entry_older_than_six_months_can_be_deleted() {
        var old = entry("6 months 1 day");

        assertThat(jdbc.update("DELETE FROM audit_log WHERE id = ?", old)).isEqualTo(1);
    }

    @Test
    void a_recent_entry_cannot_be_deleted() {
        var recent = entry("5 months 29 days");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id = ?", recent))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void no_entry_can_be_modified_even_an_old_one() {
        var old = entry("1 year");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'ALERT_HANDLED' WHERE id = ?", old))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }
}
