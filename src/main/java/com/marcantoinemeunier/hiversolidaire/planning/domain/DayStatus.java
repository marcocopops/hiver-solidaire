package com.marcantoinemeunier.hiversolidaire.planning.domain;

/** R5 Statut d'une journée. */
public enum DayStatus {
    /** Tous les minimums des postes de la journée sont couverts. */
    COMPLETE,
    /** Au moins un minimum n'est pas couvert, qu'il y ait des inscrits ou non. */
    INCOMPLETE
}
