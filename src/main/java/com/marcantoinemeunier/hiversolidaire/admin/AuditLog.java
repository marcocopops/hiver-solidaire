package com.marcantoinemeunier.hiversolidaire.admin;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal d'audit des actions des coordinateurs (cahier des charges §6, §8 ; modèle de menaces M6, M8) : qui
 * (identifiant technique du coordinateur), quand, quelle action, sur quoi (identifiants). Jamais de nom, d'e-mail ni de
 * téléphone : la table n'a aucune colonne pour cela. Écrit dans la transaction de l'action ({@code MANDATORY}) : la
 * ligne existe si et seulement si l'action a eu lieu. La table est en ajout seul (déclencheur de la migration V10).
 */
@Component
public class AuditLog {

    /** Valeurs de la contrainte {@code audit_log_action}. */
    public enum Action {
        ALERT_HANDLED,
        VOLUNTEER_REVIEWED,
        REGISTRATION_CANCELLED,
        REGISTRATION_MOVED,
        REGISTRATION_CREATED,
        VOLUNTEER_CREATED,
        VOLUNTEER_IDENTITY_CORRECTED,
        VOLUNTEER_DELETED,
        SEASON_CREATED,
        SEASON_UPDATED,
        SAMPLE_SEASON_DELETED,
        REGISTRATIONS_EXPORTED,
        PRINTABLE_PLANNING_VIEWED
    }

    /** Objets visés par une action, tous facultatifs : des identifiants techniques et des dates, rien d'autre. */
    public record Target(UUID volunteerId, UUID registrationId, UUID eveningId, UUID targetEveningId, UUID seasonId,
            UUID alertId, LocalDate periodFrom, LocalDate periodTo) {

        public static Target none() {
            return new Target(null, null, null, null, null, null, null, null);
        }

        public static Target volunteer(UUID volunteerId) {
            return new Target(volunteerId, null, null, null, null, null, null, null);
        }

        public static Target registration(UUID registrationId, UUID volunteerId, UUID eveningId) {
            return new Target(volunteerId, registrationId, eveningId, null, null, null, null, null);
        }

        public static Target move(UUID registrationId, UUID volunteerId, UUID fromEveningId, UUID toEveningId) {
            return new Target(volunteerId, registrationId, fromEveningId, toEveningId, null, null, null, null);
        }

        public static Target season(UUID seasonId) {
            return new Target(null, null, null, null, seasonId, null, null, null);
        }

        public static Target alert(UUID alertId) {
            return new Target(null, null, null, null, null, alertId, null, null);
        }

        public static Target period(LocalDate from, LocalDate to) {
            return new Target(null, null, null, null, null, null, from, to);
        }
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    AuditLog(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID coordinatorId, Action action, Target target) {
        Objects.requireNonNull(coordinatorId, "coordinatorId");
        jdbc.update("""
                INSERT INTO audit_log (id, occurred_at, coordinator_id, action, volunteer_id, registration_id,
                    evening_id, target_evening_id, season_id, alert_id, period_from, period_to)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), Timestamp.from(clock.instant()), coordinatorId, action.name(),
                target.volunteerId(), target.registrationId(), target.eveningId(), target.targetEveningId(),
                target.seasonId(), target.alertId(), target.periodFrom(), target.periodTo());
    }
}
