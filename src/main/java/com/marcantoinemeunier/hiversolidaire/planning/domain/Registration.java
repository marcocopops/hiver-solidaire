package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Inscription telle que le planning en a besoin pour calculer capacité et statut : ni bénévole, ni donnée
 * personnelle. Le lien avec le bénévole arrive avec l'inscription elle-même (S3).
 */
public record Registration(RegistrationType type, DishChoice dishChoice) {

    public Registration {
        Objects.requireNonNull(type, "type");
        type.coveredPosts(dishChoice);
    }

    public static Registration of(RegistrationType type) {
        return new Registration(type, null);
    }

    public static Registration of(RegistrationType type, DishChoice dishChoice) {
        return new Registration(type, dishChoice);
    }

    public Set<Post> coveredPosts() {
        return type.coveredPosts(dishChoice);
    }
}
