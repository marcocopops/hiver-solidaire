package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.util.List;

/**
 * Ce que « Je participe » propose sur une journée.
 *
 * @param available inscriptions encore possibles, dans l'ordre de la carte (petit-déjeuner en tête)
 * @param blocking motif à afficher quand rien n'est possible (R7, R2, ou R1 si tout est pris), sinon {@code null}
 */
public record DayOffer(List<Offer> available, RegistrationDecision blocking) {

    public DayOffer {
        available = List.copyOf(available);
    }
}
