package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Saison : de la première à la dernière soirée (dates du soir J), et date d'ouverture des inscriptions.
 * Les dates sont paramétrables (cahier des charges, §3).
 */
public record Season(LocalDate firstEvening, LocalDate lastEvening, Instant registrationsOpenAt) {

    public Season {
        Objects.requireNonNull(firstEvening, "firstEvening");
        Objects.requireNonNull(lastEvening, "lastEvening");
        Objects.requireNonNull(registrationsOpenAt, "registrationsOpenAt");
        if (lastEvening.isBefore(firstEvening)) {
            throw new IllegalArgumentException("Last evening cannot precede first evening");
        }
    }

    /** Dates des soirées de la saison, bornes comprises. */
    public List<LocalDate> eveningDates() {
        return firstEvening.datesUntil(lastEvening.plusDays(1)).toList();
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(firstEvening) && !date.isAfter(lastEvening);
    }

    /** R7 : inscriptions possibles de l'ouverture à la fin de saison, jamais sur une soirée passée ou fermée. */
    public boolean acceptsRegistrations(Evening evening, Instant now) {
        return !now.isBefore(registrationsOpenAt)
                && contains(evening.date())
                && !evening.closed()
                && !evening.isPast(ParisTime.today(now));
    }
}
