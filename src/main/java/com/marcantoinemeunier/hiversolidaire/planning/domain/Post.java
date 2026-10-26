package com.marcantoinemeunier.hiversolidaire.planning.domain;

/**
 * Postes d'une soirée (cahier des charges, §5, tableau « Besoins par soirée »). L'ordre d'affichage d'une journée est
 * {@link Day#POSTS}.
 */
public enum Post {
    MAIN_DISH,
    SIDES,
    DINNER_PRESENCE,
    NIGHT,
    BREAKFAST
}
