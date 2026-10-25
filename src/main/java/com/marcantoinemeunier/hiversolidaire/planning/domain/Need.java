package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.util.Objects;

/** Besoin d'un poste pour une soirée : le minimum rend la soirée complète (R5), le maximum borne la capacité (R1). */
public record Need(Post post, int minimum, int maximum) {

    public Need {
        Objects.requireNonNull(post, "post");
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException("Need must satisfy 0 <= minimum <= maximum for " + post);
        }
    }
}
