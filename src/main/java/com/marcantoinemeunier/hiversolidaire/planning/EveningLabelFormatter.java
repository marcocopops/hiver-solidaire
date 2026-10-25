package com.marcantoinemeunier.hiversolidaire.planning;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Libellé d'une soirée (cahier des charges, §5) : « Lun. 5 → mar. 6 janv. ». Le mois n'est répété que s'il change
 * entre le soir et le lendemain matin ; le premier du mois s'écrit « 1er ».
 */
public final class EveningLabelFormatter {

    private static final DateTimeFormatter DAY_OF_WEEK = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH);

    private EveningLabelFormatter() {
    }

    public static String format(LocalDate evening) {
        var morning = evening.plusDays(1);
        var start = capitalize(DAY_OF_WEEK.format(evening)) + " " + dayOfMonth(evening);
        if (evening.getMonth() != morning.getMonth()) {
            start += " " + MONTH.format(evening);
        }
        return start + " → " + DAY_OF_WEEK.format(morning) + " " + dayOfMonth(morning) + " " + MONTH.format(morning);
    }

    private static String dayOfMonth(LocalDate date) {
        return date.getDayOfMonth() == 1 ? "1er" : String.valueOf(date.getDayOfMonth());
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
