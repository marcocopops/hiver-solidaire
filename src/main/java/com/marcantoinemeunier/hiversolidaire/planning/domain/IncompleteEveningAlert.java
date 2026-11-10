package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Alerte « soirée devenue incomplète » (cahier des charges §6) : une annulation fait perdre un minimum à une soirée
 * des 7 prochains jours (aujourd'hui compris). Calculée par soirée, même si le planning s'affiche par journée
 * (ADR-0009). Si le dîner de la soirée commence dans moins de 48 h, l'alerte part tout de suite par e-mail ; sinon, elle
 * n'apparaît que dans le récap quotidien des coordinateurs (S5a).
 */
public final class IncompleteEveningAlert {

    /** Fenêtre de l'alerte, en jours après aujourd'hui. */
    public static final int DAYS_AHEAD = 7;

    /** Heure du dîner (§5, « livré pour 19 h ») : début de la soirée pour la règle des 48 h. */
    public static final LocalTime DINNER_START = LocalTime.of(19, 0);

    /** Fenêtre de l'e-mail immédiat, avant le début du dîner. */
    public static final Duration IMMEDIATE_WINDOW = Duration.ofHours(48);

    private IncompleteEveningAlert() {
    }

    public static boolean triggeredBy(Evening before, Evening after, LocalDate today) {
        var date = before.date();
        return !date.isBefore(today)
                && !date.isAfter(today.plusDays(DAYS_AHEAD))
                && !before.lostMinimums(after).isEmpty();
    }

    /**
     * L'alerte part tout de suite par e-mail : le dîner de la soirée (soir J à 19 h, heure de Paris) commence dans moins
     * de 48 h, ou a déjà commencé. À appeler pour une alerte déclenchée ({@link #triggeredBy}), donc pas pour une soirée
     * passée.
     */
    public static boolean immediate(LocalDate eveningDate, Instant now) {
        var dinner = eveningDate.atTime(DINNER_START).atZone(ParisTime.ZONE).toInstant();
        return now.isAfter(dinner.minus(IMMEDIATE_WINDOW));
    }
}
