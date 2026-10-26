package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Inscription encore possible sur une journée : la soirée qui la portera, le type et, s'il prépare, le plat. */
public record Offer(LocalDate eveningDate, RegistrationType type, DishChoice dishChoice) {

    public Offer {
        Objects.requireNonNull(eveningDate, "eveningDate");
        Objects.requireNonNull(type, "type");
        type.coveredPosts(dishChoice);
    }
}
