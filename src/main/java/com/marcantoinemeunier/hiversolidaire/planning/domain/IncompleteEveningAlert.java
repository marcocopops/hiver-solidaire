package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.LocalDate;

/**
 * Alerte « soirée devenue incomplète » (cahier des charges §6) : une annulation fait perdre un minimum à une soirée
 * des 7 prochains jours (aujourd'hui compris). Calculée par soirée, même si le planning s'affiche par journée
 * (ADR-0009).
 */
public final class IncompleteEveningAlert {

    /** Fenêtre de l'alerte, en jours après aujourd'hui. */
    public static final int DAYS_AHEAD = 7;

    private IncompleteEveningAlert() {
    }

    public static boolean triggeredBy(Evening before, Evening after, LocalDate today) {
        var date = before.date();
        return !date.isBefore(today)
                && !date.isAfter(today.plusDays(DAYS_AHEAD))
                && !before.lostMinimums(after).isEmpty();
    }
}
