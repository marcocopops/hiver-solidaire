package com.marcantoinemeunier.hiversolidaire.planning.domain;

/** Résultat du contrôle d'une nouvelle inscription, dans l'ordre où les règles sont vérifiées. */
public enum RegistrationDecision {
    ACCEPTED,
    /** R7 : avant l'ouverture des inscriptions. */
    NOT_OPEN_YET,
    /** R7 : soirée hors des dates de la saison. */
    OUT_OF_SEASON,
    /** R7 : soirée passée (à partir de minuit le lendemain, heure de Paris). */
    PAST,
    /** R2 : le bénévole a déjà ce poste sur cette soirée (ADR-0011). */
    ALREADY_REGISTERED,
    /** R2 : « Récupérer » ne se cumule qu'avec le petit-déjeuner, pas avec un autre poste du même dîner. */
    NOT_CUMULABLE,
    /** R3 : le plat choisi est déjà préparé par quelqu'un d'autre. */
    DISH_TAKEN,
    /** R4 : aucun repas préparé chez soi n'attend de récupération sur cette soirée. */
    NOTHING_TO_PICK_UP,
    /** R1 : un des postes couverts n'a plus de place. */
    FULL;

    public boolean accepted() {
        return this == ACCEPTED;
    }

    /**
     * Le motif vaut pour toute la soirée (R7), quel que soit le choix ; les autres ne concernent qu'un choix, et
     * « Je participe » propose simplement les autres.
     */
    public boolean blocksTheEvening() {
        return this == NOT_OPEN_YET || this == OUT_OF_SEASON || this == PAST;
    }
}
