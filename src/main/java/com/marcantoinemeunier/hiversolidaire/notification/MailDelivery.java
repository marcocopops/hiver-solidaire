package com.marcantoinemeunier.hiversolidaire.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une ligne de la table d'envois ({@code mail_delivery}, ADR-0012) : des identifiants et des dates, aucune donnée
 * personnelle. Le contenu de l'e-mail est calculé à l'envoi.
 *
 * @param volunteerId destinataire d'une confirmation ou d'un rappel
 * @param alertId alerte d'un e-mail immédiat aux coordinateurs
 * @param referenceDate journée d'un rappel (la carte du lendemain), jour d'un récap
 * @param attempts échecs d'envoi déjà subis
 */
record MailDelivery(UUID id, Kind kind, UUID volunteerId, UUID alertId, LocalDate referenceDate, Instant createdAt,
        Instant notAfter, int attempts) {

    /** Valeurs de la contrainte {@code mail_delivery_kind}. */
    enum Kind {
        /** Confirmation des inscriptions et désinscriptions d'un bénévole, regroupées. */
        CONFIRMATION,
        /** Rappel de la veille à 18 h. */
        REMINDER,
        /** Récap quotidien des coordinateurs à 20 h. */
        DAILY_RECAP,
        /** Alerte immédiate aux coordinateurs : soirée des 48 prochaines heures devenue incomplète. */
        ALERT
    }

    /** Valeurs de la contrainte {@code mail_delivery_abandon_reason}. */
    enum AbandonReason {
        /** Compte supprimé, bénévole sans adresse, aucun coordinateur. */
        NO_RECIPIENT,
        /** Plus rien à dire : inscriptions annulées entre-temps, saison terminée. */
        NOTHING_TO_SEND,
        /** Trop tard : le créneau ou la soirée est passé. */
        EXPIRED,
        /** Trop d'échecs d'envoi. */
        FAILED,
        /** Tous les destinataires sont dans un domaine réservé aux tests ({@link RecipientGuard}). */
        RESERVED_DOMAIN
    }

    /** Valeurs de la contrainte {@code mail_delivery_change_kind}. */
    enum Change {
        REGISTERED,
        CANCELLED
    }
}
