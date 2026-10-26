package com.marcantoinemeunier.hiversolidaire.planning;

import java.util.List;

/**
 * Ce que voit un visiteur (cahier des charges, §4) : une carte par journée (ADR-0009), son statut et, par poste, les
 * pseudos publics des inscrits. Aucune autre donnée personnelle, aucun identifiant technique. Un bénévole connecté
 * voit en plus « Vous » sur ses propres postes et de quoi se désinscrire, rien sur les autres.
 *
 * @param opensOn date d'ouverture des inscriptions si elle est à venir, sinon {@code null}
 * @param filter filtre appliqué, après repli sur « Toutes » d'une valeur inconnue
 * @param signedIn un bénévole est connecté : le filtre « Les miennes » est proposé
 * @param anyUpcoming il reste des journées à venir, avant filtrage (pour distinguer les deux messages « aucune »)
 * @param days journées à venir retenues par le filtre, dans l'ordre des dates
 */
record PublicPlanning(String seasonLabel, String opensOn, PlanningFilter filter, boolean signedIn,
        boolean anyUpcoming, List<PublicDay> days) {

    static PublicPlanning none(PlanningFilter filter, boolean signedIn) {
        return new PublicPlanning(null, null, filter, signedIn, false, List.of());
    }

    /**
     * Une journée telle qu'affichée.
     *
     * @param date date du jour (ISO), seule clé publique d'une journée : aucun identifiant technique
     * @param statusKey suffixe des clés de message et des classes CSS : complete ou incomplete
     * @param open carte dépliée : la journée est à compléter
     * @param registrable « Je participe » : au moins une inscription encore possible sur l'une des deux soirées
     * @param posts postes de la journée dans l'ordre d'affichage (petit-déjeuner, plat, accompagnements, dîner, nuit)
     * @param mine inscriptions actives du bénévole connecté sur cette journée (ses propres données seulement)
     */
    record PublicDay(String date, String label, String statusKey, boolean open, boolean registrable,
            List<PostLine> posts, List<MyRegistration> mine) {
    }

    /**
     * Un poste de la journée.
     *
     * @param post nom du poste, pour les classes CSS
     * @param titleKey clé de message du nom du poste
     * @param hintKey clé d'une précision (« jusqu'au matin » pour la nuit), ou {@code null}
     * @param you le bénévole connecté occupe ce poste : « Vous », à la place de son pseudo
     * @param pseudos pseudos publics des autres inscrits, par ordre alphabétique (pas l'ordre d'inscription)
     * @param missingSlots places à pourvoir jusqu'au minimum (R5)
     */
    record PostLine(String post, String titleKey, String hintKey, boolean you, List<String> pseudos,
            int missingSlots) {
    }

    /**
     * Une inscription du bénévole connecté sur la carte, avec sa désinscription (R6).
     *
     * @param eveningDate date de la soirée qui porte l'inscription (ISO) : paramètre {@code soiree} de la page de
     *     confirmation de S4a ; la veille de la journée pour un petit-déjeuner
     * @param youKey clé de la phrase « Vous passez la nuit », etc.
     * @param cancelKey clé du bouton : « Me désinscrire », ou « … du petit-déjeuner » s'il y en a deux sur la carte
     * @param cancellable le bénévole peut encore se désinscrire seul
     * @param deadline date limite calculée (« jeu. 7 janv. à 23 h 59 »)
     */
    record MyRegistration(String eveningDate, String youKey, String cancelKey, boolean cancellable,
            String deadline) {
    }
}
