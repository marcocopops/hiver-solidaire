package com.marcantoinemeunier.hiversolidaire.planning;

import java.util.List;

/**
 * Ce que voit un visiteur (cahier des charges, §4) : le statut des soirées et, par poste, les pseudos publics des
 * inscrits. Aucune autre donnée personnelle, aucun identifiant technique. Un bénévole connecté voit en plus les
 * soirées où il est lui-même inscrit.
 *
 * @param opensOn date d'ouverture des inscriptions si elle est à venir, sinon {@code null}
 */
record PublicPlanning(String seasonLabel, boolean sample, String opensOn, List<PublicEvening> evenings) {

    static PublicPlanning none() {
        return new PublicPlanning(null, false, null, List.of());
    }

    /**
     * Une soirée telle qu'affichée publiquement.
     *
     * @param date date du soir J (ISO), seule clé publique d'une soirée : aucun identifiant technique
     * @param statusKey suffixe des clés de message et des classes CSS : complete, partial, empty ou closed
     * @param missingPostKeys clés de message des postes dont le minimum n'est pas couvert
     * @param registrable inscriptions ouvertes (R7) et au moins un type encore disponible (R1, R3)
     * @param mine le bénévole connecté est inscrit sur cette soirée (ses propres données seulement)
     * @param registrants pseudos des inscrits par poste, postes vides omis ; vide sur une soirée fermée
     */
    record PublicEvening(String date, String label, String statusKey, List<String> missingPostKeys,
            boolean registrable, boolean mine, List<PostRegistrants> registrants) {
    }

    /**
     * Inscrits d'un poste.
     *
     * @param titleKey clé de message du nom du poste
     * @param pseudos pseudos publics, par ordre alphabétique (l'ordre d'inscription n'est pas montré)
     */
    record PostRegistrants(String titleKey, List<String> pseudos) {
    }
}
