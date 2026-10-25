package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Inscription telle que les règles en ont besoin : type, plat éventuel et identifiant technique du bénévole
 * (R2). Aucune donnée personnelle. {@code volunteerId} est nul quand seul le calcul de capacité compte.
 */
public record Registration(RegistrationType type, DishChoice dishChoice, UUID volunteerId) {

    public Registration {
        Objects.requireNonNull(type, "type");
        type.coveredPosts(dishChoice);
    }

    public static Registration of(RegistrationType type) {
        return new Registration(type, null, null);
    }

    public static Registration of(RegistrationType type, DishChoice dishChoice) {
        return new Registration(type, dishChoice, null);
    }

    public static Registration by(UUID volunteerId, RegistrationType type, DishChoice dishChoice) {
        return new Registration(type, dishChoice, Objects.requireNonNull(volunteerId, "volunteerId"));
    }

    public Set<Post> coveredPosts() {
        return type.coveredPosts(dishChoice);
    }
}
