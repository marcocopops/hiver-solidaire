package com.marcantoinemeunier.hiversolidaire.planning;

import java.util.List;

/**
 * Ce que voit un visiteur (cahier des charges, §4) : aucune donnée personnelle, aucun identifiant technique,
 * aucun nombre d'inscrits.
 */
record PublicPlanning(String seasonLabel, boolean sample, List<PublicEvening> evenings) {

    static PublicPlanning none() {
        return new PublicPlanning(null, false, List.of());
    }

    /**
     * Une soirée telle qu'affichée publiquement.
     *
     * @param statusKey suffixe des clés de message et des classes CSS : complete, partial, empty ou closed
     * @param missingPostKeys clés de message des postes dont le minimum n'est pas couvert
     */
    record PublicEvening(String label, String statusKey, List<String> missingPostKeys) {
    }
}
