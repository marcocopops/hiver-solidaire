package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * R6 Désinscription : en autonomie jusqu'à la veille de la soirée à 23 h 59, heure de Paris. L'activité est le soir J
 * pour tous les types, petit-déjeuner du matin J+1 compris : la soirée est l'unité du planning, et un désistement à
 * minuit pour un petit-déjeuner à 7 h ne laisserait pas le temps de trouver quelqu'un. Ensuite, le bénévole appelle
 * un coordinateur.
 */
public final class Cancellation {

    private static final LocalTime LAST_MINUTE = LocalTime.of(23, 59);

    private Cancellation() {
    }

    /** Vrai jusqu'à la fin de la veille (23 h 59 min 59 s), heure de Paris, changement d'heure compris. */
    public static boolean allowed(LocalDate eveningDate, Instant now) {
        return ParisTime.today(now).isBefore(eveningDate);
    }

    /** Limite affichée au bénévole : la veille à 23 h 59, heure de Paris. */
    public static LocalDateTime deadline(LocalDate eveningDate) {
        return eveningDate.minusDays(1).atTime(LAST_MINUTE);
    }
}
