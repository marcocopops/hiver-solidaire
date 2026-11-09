package com.marcantoinemeunier.hiversolidaire.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertes aux coordinateurs à envoyer (table {@code coordinator_alert}, ADR-0008). Écrites dans la transaction de
 * leur cause : une alerte n'existe que si l'annulation a bien eu lieu, et elle survit à un redémarrage. L'envoi par
 * e-mail arrive en S5 : il lira les lignes sans {@code sent_at}. Aucune donnée personnelle, la soirée seule.
 */
@Component
public class CoordinatorAlerts {

    /** Valeurs de la contrainte {@code coordinator_alert_kind}. */
    public enum Kind {
        /** Une soirée des 7 prochains jours a perdu un minimum (cahier des charges §6). */
        EVENING_INCOMPLETE,
        /** R4 : une préparation chez soi déjà récupérée a été annulée ; la récupération reste, sans repas (lot 3). */
        PICKUP_WITHOUT_MEAL
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    CoordinatorAlerts(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Alerte enregistrée qu'aucun coordinateur n'a encore marquée traitée (tableau de bord, S4b).
     *
     * @param eveningDate date du soir J de la soirée concernée
     */
    public record Unhandled(UUID id, Kind kind, LocalDate eveningDate, Instant createdAt) {
    }

    /** Alertes non traitées, les plus anciennes d'abord. */
    @Transactional(readOnly = true)
    public List<Unhandled> unhandled() {
        return jdbc.query("""
                SELECT a.id, a.kind, e.evening_date, a.created_at FROM coordinator_alert a
                JOIN evening e ON e.id = a.evening_id
                WHERE a.handled_at IS NULL ORDER BY a.created_at, a.id
                """, (row, index) -> new Unhandled(row.getObject("id", UUID.class), Kind.valueOf(row.getString("kind")),
                row.getObject("evening_date", LocalDate.class), row.getTimestamp("created_at").toInstant()));
    }

    /**
     * Marque l'alerte traitée par ce coordinateur.
     *
     * @return faux si l'alerte n'existe pas ou était déjà traitée
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markHandled(UUID alertId, UUID coordinatorId) {
        return jdbc.update("UPDATE coordinator_alert SET handled_at = ?, handled_by = ? WHERE id = ? AND handled_at IS NULL",
                Timestamp.from(clock.instant()), coordinatorId, alertId) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void eveningIncomplete(UUID eveningId) {
        record(Kind.EVENING_INCOMPLETE, eveningId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void pickupWithoutMeal(UUID eveningId) {
        record(Kind.PICKUP_WITHOUT_MEAL, eveningId);
    }

    private void record(Kind kind, UUID eveningId) {
        jdbc.update("INSERT INTO coordinator_alert (id, kind, evening_id, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), kind.name(), eveningId, Timestamp.from(clock.instant()));
    }
}
