package com.marcantoinemeunier.hiversolidaire.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marcantoinemeunier.hiversolidaire.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migration V17 (ADR-0012) : la base garantit qu'un e-mail n'est créé qu'une fois (un rappel par bénévole et par
 * journée, un récap par jour, un e-mail par alerte, une confirmation ouverte par bénévole), et qu'une ligne n'est jamais
 * à la fois envoyée et abandonnée.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MailDeliverySchemaTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);
    private static final Timestamp NOW = Timestamp.from(Instant.parse("2027-03-09T17:00:00Z"));

    @Autowired
    JdbcTemplate jdbc;

    private UUID volunteer() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO volunteer (id, email) VALUES (?, ?)", id, id + "@example.org");
        return id;
    }

    private UUID alert() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO coordinator_alert (id, kind, evening_id, created_at)
                SELECT ?, 'EVENING_INCOMPLETE', e.id, ? FROM evening e WHERE e.evening_date = ?
                """, id, NOW, DAY);
        return id;
    }

    private int insert(String kind, UUID volunteerId, UUID alertId, LocalDate referenceDate, Timestamp closedAt) {
        return jdbc.update("""
                INSERT INTO mail_delivery (id, kind, volunteer_id, alert_id, reference_date, created_at, closed_at,
                    next_attempt_at, not_after)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), kind, volunteerId, alertId, referenceDate, NOW, closedAt, NOW, NOW);
    }

    /**
     * L'instruction est refusée par la base. Un point de sauvegarde l'isole : PostgreSQL abandonne sinon le reste de la
     * transaction du test.
     */
    private void assertRefused(Runnable statement) {
        jdbc.execute("SAVEPOINT refused");
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT refused");
    }

    @Test
    void a_second_reminder_for_the_same_volunteer_and_day_is_refused() {
        var alice = volunteer();
        insert("REMINDER", alice, null, DAY, NOW);

        assertRefused(() -> insert("REMINDER", alice, null, DAY, NOW));
        assertThat(insert("REMINDER", alice, null, DAY.plusDays(1), NOW)).isEqualTo(1);
        assertThat(insert("REMINDER", volunteer(), null, DAY, NOW)).isEqualTo(1);
    }

    @Test
    void a_second_recap_for_the_same_day_is_refused() {
        insert("DAILY_RECAP", null, null, DAY, NOW);

        assertRefused(() -> insert("DAILY_RECAP", null, null, DAY, NOW));
    }

    @Test
    void an_alert_is_mailed_once() {
        var alert = alert();
        insert("ALERT", null, alert, null, NOW);

        assertRefused(() -> insert("ALERT", null, alert, null, NOW));
    }

    @Test
    void a_volunteer_has_one_open_confirmation_at_most() {
        var alice = volunteer();
        insert("CONFIRMATION", alice, null, null, null);

        assertRefused(() -> insert("CONFIRMATION", alice, null, null, null));
        // Une confirmation fermée (en cours d'envoi ou envoyée) laisse s'en ouvrir une nouvelle.
        jdbc.update("UPDATE mail_delivery SET closed_at = ? WHERE volunteer_id = ?", NOW, alice);
        assertThat(insert("CONFIRMATION", alice, null, null, null)).isEqualTo(1);
    }

    @Test
    void only_a_confirmation_may_be_open() {
        assertRefused(() -> insert("DAILY_RECAP", null, null, DAY, null));
    }

    @Test
    void each_kind_has_its_own_shape() {
        assertRefused(() -> insert("REMINDER", volunteer(), null, null, NOW));
        assertRefused(() -> insert("DAILY_RECAP", volunteer(), null, DAY, NOW));
        assertRefused(() -> insert("ALERT", null, null, null, NOW));
        assertRefused(() -> insert("CONFIRMATION", null, null, null, null));
        assertRefused(() -> insert("NEWSLETTER", null, null, DAY, NOW));
    }

    @Test
    void a_delivery_is_never_both_sent_and_abandoned() {
        insert("DAILY_RECAP", null, null, DAY, NOW);

        assertRefused(() -> jdbc.update("""
                UPDATE mail_delivery SET sent_at = ?, abandoned_at = ?, abandon_reason = 'FAILED'
                WHERE kind = 'DAILY_RECAP' AND reference_date = ?
                """, NOW, NOW, DAY));
        assertRefused(() -> jdbc.update("""
                UPDATE mail_delivery SET abandoned_at = ? WHERE kind = 'DAILY_RECAP' AND reference_date = ?
                """, NOW, DAY));
    }

    @Test
    void an_open_confirmation_cannot_be_sent() {
        var alice = volunteer();
        insert("CONFIRMATION", alice, null, null, null);

        assertRefused(() -> jdbc.update("UPDATE mail_delivery SET sent_at = ? WHERE volunteer_id = ?", NOW, alice));
    }

    @Test
    void the_table_has_no_column_for_personal_data() {
        var columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name IN ('mail_delivery', 'mail_delivery_change')
                """, String.class);

        assertThat(columns).noneMatch(column -> column.contains("email") || column.contains("name")
                || column.contains("phone") || column.contains("subject") || column.contains("body")
                || column.contains("recipient"));
    }
}
