package com.marcantoinemeunier.hiversolidaire.planning;

import java.util.Arrays;

/**
 * Filtres du planning (§6), en GET sans JavaScript : {@code /planning?filtre=…}. Une valeur inconnue revient à
 * « Toutes », comme « Les miennes » sans connexion : la page ne recopie jamais le paramètre.
 */
enum PlanningFilter {
    ALL("toutes"),
    TO_COMPLETE("a-completer"),
    MINE("miennes");

    static final String PARAM = "filtre";

    private final String param;

    PlanningFilter(String param) {
        this.param = param;
    }

    /** Valeur du paramètre {@code filtre}. */
    String param() {
        return param;
    }

    static PlanningFilter parse(String value, boolean signedIn) {
        var filter = Arrays.stream(values()).filter(candidate -> candidate.param.equals(value)).findFirst()
                .orElse(ALL);
        return filter == MINE && !signedIn ? ALL : filter;
    }
}
