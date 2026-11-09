package com.marcantoinemeunier.hiversolidaire.admin;

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

/** Journal d'audit (M6, M8) : identifiants seulement, en ajout seul. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditLogSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void the_audit_log_has_no_column_for_personal_data() {
        var columns = jdbc.queryForList("""
                SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'audit_log'
                """);
        assertThat(columns).allSatisfy(column -> {
            var name = (String) column.get("column_name");
            var type = (String) column.get("data_type");
            if (name.equals("action")) {
                assertThat(type).isEqualTo("character varying");
            } else {
                assertThat(type).as(name).isIn("uuid", "date", "timestamp with time zone");
            }
        });
    }

    @Test
    void the_action_is_one_of_the_known_values() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO audit_log (id, occurred_at, coordinator_id, action) VALUES (?, now(), ?, 'anne@example.org')
                """, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void the_audit_log_is_append_only() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_log (id, occurred_at, coordinator_id, action) VALUES (?, now(), ?, 'ALERT_HANDLED')",
                id, UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET coordinator_id = ? WHERE id = ?", UUID.randomUUID(),
                id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void every_action_of_the_code_is_accepted_by_the_database() {
        for (var action : AuditLog.Action.values()) {
            jdbc.update("INSERT INTO audit_log (id, occurred_at, coordinator_id, action) VALUES (?, now(), ?, ?)",
                    UUID.randomUUID(), UUID.randomUUID(), action.name());
        }
    }
}
