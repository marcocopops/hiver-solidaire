package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.AbandonReason;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Change;
import com.marcantoinemeunier.hiversolidaire.notification.MailDelivery.Kind;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès à la table d'envois (ADR-0012). Requêtes paramétrées seulement. Les créations passent par
 * {@code INSERT … ON CONFLICT DO NOTHING} sur les index uniques de V17 : la base refuse les doublons, même après un
 * redémarrage.
 */
@Repository
class MailDeliveries {

    private static final String COLUMNS = """
            id, kind, volunteer_id, alert_id, reference_date, created_at, not_after, attempts
            """;

    private final JdbcTemplate jdbc;

    MailDeliveries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Rappel de la journée {@code day} pour ce bénévole, s'il n'existe pas déjà. */
    boolean createReminder(UUID volunteerId, LocalDate day, Instant now, Instant notAfter) {
        return jdbc.update("""
                INSERT INTO mail_delivery (id, kind, volunteer_id, reference_date, created_at, closed_at, next_attempt_at,
                    not_after)
                VALUES (?, 'REMINDER', ?, ?, ?, ?, ?, ?)
                ON CONFLICT (volunteer_id, reference_date) WHERE kind = 'REMINDER' DO NOTHING
                """, UUID.randomUUID(), volunteerId, day, ts(now), ts(now), ts(now), ts(notAfter)) == 1;
    }

    /** Récap du jour {@code day}, s'il n'existe pas déjà. */
    boolean createRecap(LocalDate day, Instant now, Instant notAfter) {
        return jdbc.update("""
                INSERT INTO mail_delivery (id, kind, reference_date, created_at, closed_at, next_attempt_at, not_after)
                VALUES (?, 'DAILY_RECAP', ?, ?, ?, ?, ?)
                ON CONFLICT (reference_date) WHERE kind = 'DAILY_RECAP' DO NOTHING
                """, UUID.randomUUID(), day, ts(now), ts(now), ts(now), ts(notAfter)) == 1;
    }

    /** E-mail immédiat d'une alerte, s'il n'existe pas déjà. */
    boolean createAlert(UUID alertId, Instant now, Instant notAfter) {
        return jdbc.update("""
                INSERT INTO mail_delivery (id, kind, alert_id, created_at, closed_at, next_attempt_at, not_after)
                VALUES (?, 'ALERT', ?, ?, ?, ?, ?)
                ON CONFLICT (alert_id) WHERE kind = 'ALERT' DO NOTHING
                """, UUID.randomUUID(), alertId, ts(now), ts(now), ts(now), ts(notAfter)) == 1;
    }

    /**
     * Ajoute un changement à la confirmation ouverte du bénévole, créée au besoin, et repousse son envoi : les
     * changements rapprochés partent ensemble. La ligne ouverte est verrouillée jusqu'à la fin de la transaction ;
     * l'envoi la ferme avant de la lire ({@link #claimDue}), donc un changement n'est jamais perdu entre les deux.
     */
    void addToOpenConfirmation(UUID volunteerId, UUID registrationId, Change change, boolean byCoordinator,
            Instant now, Instant sendAt, Instant notAfter) {
        var deliveryId = openConfirmation(volunteerId, now, sendAt, notAfter);
        jdbc.update("UPDATE mail_delivery SET next_attempt_at = ?, not_after = ? WHERE id = ?",
                ts(sendAt), ts(notAfter), deliveryId);
        jdbc.update("""
                INSERT INTO mail_delivery_change (delivery_id, registration_id, change, by_coordinator, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (delivery_id, registration_id, change) DO NOTHING
                """, deliveryId, registrationId, change.name(), byCoordinator, ts(now));
    }

    /**
     * Confirmation ouverte du bénévole, verrouillée. Si l'envoi la ferme entre la création et la lecture, une nouvelle
     * est ouverte : on recommence.
     */
    private UUID openConfirmation(UUID volunteerId, Instant now, Instant sendAt, Instant notAfter) {
        for (var attempt = 0; attempt < 3; attempt++) {
            jdbc.update("""
                    INSERT INTO mail_delivery (id, kind, volunteer_id, created_at, next_attempt_at, not_after)
                    VALUES (?, 'CONFIRMATION', ?, ?, ?, ?)
                    ON CONFLICT (volunteer_id) WHERE kind = 'CONFIRMATION' AND closed_at IS NULL DO NOTHING
                    """, UUID.randomUUID(), volunteerId, ts(now), ts(sendAt), ts(notAfter));
            var open = jdbc.queryForList("""
                    SELECT id FROM mail_delivery WHERE kind = 'CONFIRMATION' AND volunteer_id = ? AND closed_at IS NULL
                    FOR UPDATE
                    """, UUID.class, volunteerId);
            if (!open.isEmpty()) {
                return open.getFirst();
            }
        }
        throw new IllegalStateException("No open confirmation could be found or created");
    }

    /**
     * Lignes à envoyer maintenant, fermées dans la même transaction : plus aucun changement ne s'ajoute à une confirmation
     * dont l'envoi commence. {@code SKIP LOCKED} laisse de côté une confirmation qu'une inscription est en train de
     * compléter : elle partira au tour suivant.
     */
    List<UUID> claimDue(Instant now, int limit) {
        var ids = jdbc.queryForList("""
                SELECT id FROM mail_delivery
                WHERE sent_at IS NULL AND abandoned_at IS NULL AND next_attempt_at <= ?
                ORDER BY next_attempt_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, UUID.class, ts(now), limit);
        for (var id : ids) {
            jdbc.update("UPDATE mail_delivery SET closed_at = COALESCE(closed_at, ?) WHERE id = ?", ts(now), id);
        }
        return ids;
    }

    Optional<MailDelivery> pending(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM mail_delivery WHERE id = ? AND sent_at IS NULL AND abandoned_at IS NULL",
                MailDeliveries::map, id).stream().findFirst();
    }

    /** Changements regroupés dans une confirmation, dans l'ordre où ils ont eu lieu. */
    List<ChangeRow> changes(UUID deliveryId) {
        return jdbc.query("""
                SELECT registration_id, change, by_coordinator FROM mail_delivery_change
                WHERE delivery_id = ? ORDER BY occurred_at, registration_id, change
                """, (row, index) -> new ChangeRow(row.getObject("registration_id", UUID.class),
                Change.valueOf(row.getString("change")), row.getBoolean("by_coordinator")), deliveryId);
    }

    record ChangeRow(UUID registrationId, Change change, boolean byCoordinator) {
    }

    /** Envoi réussi ; pour une alerte, {@code coordinator_alert.sent_at} aussi (V8). */
    void markSent(UUID id, Instant now) {
        jdbc.update("UPDATE mail_delivery SET sent_at = ? WHERE id = ? AND sent_at IS NULL AND abandoned_at IS NULL",
                ts(now), id);
        jdbc.update("""
                UPDATE coordinator_alert SET sent_at = ?
                WHERE sent_at IS NULL AND id = (SELECT alert_id FROM mail_delivery WHERE id = ?)
                """, ts(now), id);
    }

    /** Échec d'envoi : nouvel essai à {@code retryAt}. */
    void recordFailure(UUID id, Instant retryAt) {
        jdbc.update("UPDATE mail_delivery SET attempts = attempts + 1, next_attempt_at = ? WHERE id = ?",
                ts(retryAt), id);
    }

    void abandon(UUID id, AbandonReason reason, Instant now) {
        jdbc.update("""
                UPDATE mail_delivery SET abandoned_at = ?, abandon_reason = ?, closed_at = COALESCE(closed_at, ?)
                WHERE id = ? AND sent_at IS NULL AND abandoned_at IS NULL
                """, ts(now), reason.name(), ts(now), id);
    }

    /** Envois expirés, sans canal d'envoi pour les traiter : abandonnés pour ne pas rester en attente. */
    int abandonExpired(Instant now) {
        return jdbc.update("""
                UPDATE mail_delivery SET abandoned_at = ?, abandon_reason = 'EXPIRED', closed_at = COALESCE(closed_at, ?)
                WHERE sent_at IS NULL AND abandoned_at IS NULL AND not_after < ?
                """, ts(now), ts(now), ts(now));
    }

    /** Suppression du compte : plus rien ne part vers ce bénévole. */
    void abandonPendingOf(UUID volunteerId, Instant now) {
        jdbc.update("""
                UPDATE mail_delivery SET abandoned_at = ?, abandon_reason = 'NO_RECIPIENT', closed_at = COALESCE(closed_at, ?)
                WHERE volunteer_id = ? AND sent_at IS NULL AND abandoned_at IS NULL
                """, ts(now), ts(now), volunteerId);
    }

    /**
     * Bénévoles à qui rappeler la journée {@code day} (carte du planning, ADR-0009) : compte actif avec une adresse, et
     * au moins une inscription active montrée ce jour-là, enregistrée avant l'heure du rappel. Le petit-déjeuner du jour
     * appartient à la soirée de la veille.
     */
    List<UUID> volunteersToRemind(LocalDate day, Instant registeredBefore) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.volunteer_id FROM registration r
                JOIN evening e ON e.id = r.evening_id
                JOIN volunteer v ON v.id = r.volunteer_id
                WHERE r.cancelled_at IS NULL AND v.deleted_at IS NULL AND v.email IS NOT NULL AND r.created_at < ?
                    AND ((e.evening_date = ? AND r.type = 'BREAKFAST') OR (e.evening_date = ? AND r.type <> 'BREAKFAST'))
                """, UUID.class, ts(registeredBefore), day.minusDays(1), day);
    }

    /** Inscription citée dans un e-mail : sa soirée, son choix, et si elle est encore active. */
    record RegistrationRow(UUID id, LocalDate eveningDate, RegistrationType type, DishChoice dishChoice,
            boolean active) {
    }

    /** Inscriptions par identifiant, annulées comprises (une confirmation cite aussi les désinscriptions). */
    List<RegistrationRow> registrations(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT r.id, e.evening_date, r.type, r.dish_choice, r.cancelled_at IS NULL AS active
                FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.id = ANY (?)
                """, MailDeliveries::registration, (Object) ids.toArray(UUID[]::new));
    }

    /** Inscriptions actives du bénévole montrées sur la carte {@code day} (ADR-0009). */
    List<RegistrationRow> activeOnDay(UUID volunteerId, LocalDate day) {
        return jdbc.query("""
                SELECT r.id, e.evening_date, r.type, r.dish_choice, true AS active
                FROM registration r JOIN evening e ON e.id = r.evening_id
                WHERE r.volunteer_id = ? AND r.cancelled_at IS NULL
                    AND ((e.evening_date = ? AND r.type = 'BREAKFAST') OR (e.evening_date = ? AND r.type <> 'BREAKFAST'))
                """, MailDeliveries::registration, volunteerId, day.minusDays(1), day);
    }

    private static RegistrationRow registration(ResultSet row, int index) throws SQLException {
        var dish = row.getString("dish_choice");
        return new RegistrationRow(row.getObject("id", UUID.class), row.getObject("evening_date", LocalDate.class),
                RegistrationType.valueOf(row.getString("type")), dish == null ? null : DishChoice.valueOf(dish),
                row.getBoolean("active"));
    }

    /** Alerte d'un e-mail immédiat : sa soirée, et si un coordinateur l'a déjà traitée. */
    record AlertRow(UUID id, LocalDate eveningDate, boolean handled) {
    }

    Optional<AlertRow> alert(UUID alertId) {
        return jdbc.query("""
                SELECT a.id, e.evening_date, a.handled_at IS NOT NULL AS handled
                FROM coordinator_alert a JOIN evening e ON e.id = a.evening_id WHERE a.id = ?
                """, (row, index) -> new AlertRow(row.getObject("id", UUID.class),
                row.getObject("evening_date", LocalDate.class), row.getBoolean("handled")), alertId)
                .stream().findFirst();
    }

    /** Adresse d'un bénévole actif, lue à l'envoi : vide pour un compte supprimé ou inscrit par téléphone. */
    Optional<String> activeEmail(UUID volunteerId) {
        return jdbc.queryForList("SELECT email FROM volunteer WHERE id = ? AND deleted_at IS NULL AND email IS NOT NULL",
                String.class, volunteerId).stream().findFirst();
    }

    private static MailDelivery map(ResultSet row, int index) throws SQLException {
        return new MailDelivery(row.getObject("id", UUID.class), Kind.valueOf(row.getString("kind")),
                row.getObject("volunteer_id", UUID.class), row.getObject("alert_id", UUID.class),
                row.getObject("reference_date", LocalDate.class), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("not_after").toInstant(), row.getInt("attempts"));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
