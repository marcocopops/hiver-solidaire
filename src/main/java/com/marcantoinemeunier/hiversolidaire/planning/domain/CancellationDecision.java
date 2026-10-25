package com.marcantoinemeunier.hiversolidaire.planning.domain;

/** Résultat du contrôle d'une désinscription (R6). */
public enum CancellationDecision {
    ACCEPTED,
    /** Le bénévole n'a pas d'inscription active sur cette soirée. */
    NOT_REGISTERED,
    /** R6 : après la veille à 23 h 59, heure de Paris ; le bénévole appelle un coordinateur. */
    TOO_LATE;

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
