package com.marcantoinemeunier.hiversolidaire.notification;

import java.sql.Timestamp;
import java.time.Clock;
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
        EVENING_INCOMPLETE
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    CoordinatorAlerts(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void eveningIncomplete(UUID eveningId) {
        jdbc.update("INSERT INTO coordinator_alert (id, kind, evening_id, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), Kind.EVENING_INCOMPLETE.name(), eveningId, Timestamp.from(clock.instant()));
    }
}
