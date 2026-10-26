package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Inscription montrée sur une journée, avec la soirée qui la porte (D-1 pour un petit-déjeuner, D sinon). */
public record DayRegistration(LocalDate eveningDate, Registration registration) {

    public DayRegistration {
        Objects.requireNonNull(eveningDate, "eveningDate");
        Objects.requireNonNull(registration, "registration");
    }
}
