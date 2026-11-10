package com.marcantoinemeunier.hiversolidaire.admin;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Période des exports (tableau à l'écran, page imprimable, CSV), bornes comprises, selon le jour de l'activité.
 * Bornée à {@value #MAX_DAYS} jours : la page reste rapide et une saison s'exporte en quelques fois.
 */
record ExportPeriod(LocalDate from, LocalDate to) {

    /** Durée maximale d'une période, en jours. */
    static final int MAX_DAYS = 31;
    /** Période par défaut : aujourd'hui et les 6 jours suivants. */
    static final int DEFAULT_DAYS = 7;

    /** Période saisie ; les 7 prochains jours si aucune date n'est donnée (première ouverture de la page). */
    static ExportPeriod orDefault(String from, String to, LocalDate today) {
        if (from == null && to == null) {
            return new ExportPeriod(today, today.plusDays(DEFAULT_DAYS - 1));
        }
        return parse(from, to);
    }

    /**
     * Période saisie, les deux dates exigées.
     *
     * @throws InvalidPeriodException date absente ou mal formée, fin avant le début, plus de {@value #MAX_DAYS} jours
     */
    static ExportPeriod parse(String from, String to) {
        var start = date(from);
        var end = date(to);
        if (end.isBefore(start)) {
            throw new InvalidPeriodException("admin.exports.error.order");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw new InvalidPeriodException("admin.exports.error.tooLong");
        }
        return new ExportPeriod(start, end);
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidPeriodException("admin.exports.error.date");
        }
    }

    /** Période refusée : clé du message à afficher, jamais la valeur reçue. */
    static final class InvalidPeriodException extends RuntimeException {

        private final String messageKey;

        InvalidPeriodException(String messageKey) {
            super(messageKey, null, false, false);
            this.messageKey = messageKey;
        }

        String messageKey() {
            return messageKey;
        }
    }
}
