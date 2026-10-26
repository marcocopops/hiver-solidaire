package com.marcantoinemeunier.hiversolidaire.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Libellé d'une journée (cahier des charges, §5, ADR-0009) : « Lun. 4 janv. », ou « lun. 4 janv. » dans une phrase
 * (« Petit-déjeuner du mar. 5 janv. »). Le premier du mois s'écrit « 1er ».
 */
public final class DayLabelFormatter {

    private static final DateTimeFormatter DAY_OF_WEEK = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRENCH);

    private DayLabelFormatter() {
    }

    /** En tête de carte ou de ligne : « Lun. 4 janv. ». */
    public static String format(LocalDate day) {
        var inline = inline(day);
        return Character.toUpperCase(inline.charAt(0)) + inline.substring(1);
    }

    /** Dans une phrase : « lun. 4 janv. ». */
    public static String inline(LocalDate day) {
        var dayOfMonth = day.getDayOfMonth() == 1 ? "1er" : String.valueOf(day.getDayOfMonth());
        return DAY_OF_WEEK.format(day) + " " + dayOfMonth + " " + MONTH.format(day);
    }

    /** Date limite de désinscription (R6), calculée, jamais une formule : « jeu. 7 janv. à 23 h 59 ». */
    public static String deadline(LocalDateTime deadline) {
        return inline(deadline.toLocalDate()) + " à " + TIME.format(deadline);
    }
}
