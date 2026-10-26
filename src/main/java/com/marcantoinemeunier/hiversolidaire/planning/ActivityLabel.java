package com.marcantoinemeunier.hiversolidaire.planning;

import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Ce que fait une inscription et quel jour (ADR-0009) : « Petit-déjeuner du mar. 5 janv. », « Dîner du lun. 4 janv. ».
 * La date est celle de l'activité, comme sur le planning, pas celle de la soirée. Rendu par
 * {@code #messages.msg(label.key, label.date)}. Sérialisable : il passe en attribut flash, donc dans la session JDBC.
 *
 * @param key clé de message, avec la date en paramètre {0}
 * @param date jour de l'activité, en minuscules (« mar. 5 janv. »)
 */
public record ActivityLabel(String key, String date) implements Serializable {

    public static ActivityLabel of(RegistrationType type, LocalDate eveningDate) {
        var key = switch (type) {
            case BREAKFAST, NIGHT_AND_DINNER -> "activity." + type.name();
            case PREPARE_AND_SHARE, PREPARE_AT_HOME, PICK_UP_AND_SHARE -> "activity.DINNER";
        };
        return new ActivityLabel(key, DayLabelFormatter.inline(type.activityDate(eveningDate)));
    }
}
