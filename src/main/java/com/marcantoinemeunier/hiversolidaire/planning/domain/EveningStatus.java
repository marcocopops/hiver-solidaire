package com.marcantoinemeunier.hiversolidaire.planning.domain;

/** R5 Statut d'une soirée. */
public enum EveningStatus {
    /** Aucune inscription. */
    EMPTY,
    /** Au moins une inscription, mais un minimum n'est pas couvert. */
    PARTIAL,
    /** Tous les minimums sont couverts. */
    COMPLETE
}
