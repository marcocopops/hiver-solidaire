package com.marcantoinemeunier.hiversolidaire.planning.domain;

/** Résultat du contrôle d'une nouvelle inscription, dans l'ordre où les règles sont vérifiées. */
public enum RegistrationDecision {
    ACCEPTED,
    /** R7 : avant l'ouverture des inscriptions. */
    NOT_OPEN_YET,
    /** R7 : soirée hors des dates de la saison. */
    OUT_OF_SEASON,
    /** R7 : soirée fermée par un coordinateur. */
    CLOSED,
    /** R7 : soirée passée (à partir de minuit le lendemain, heure de Paris). */
    PAST,
    /** R2 : le bénévole est déjà inscrit sur cette soirée. */
    ALREADY_REGISTERED,
    /** R3 : le plat choisi est déjà préparé par quelqu'un d'autre. */
    DISH_TAKEN,
    /** R1 : un des postes couverts n'a plus de place. */
    FULL;

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
