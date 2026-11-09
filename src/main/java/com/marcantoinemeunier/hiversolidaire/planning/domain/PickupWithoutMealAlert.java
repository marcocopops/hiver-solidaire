package com.marcantoinemeunier.hiversolidaire.planning.domain;

/**
 * Alerte « récupération sans repas » (R4, lot 3) : la désinscription, le déplacement ou la suppression du compte d'un
 * bénévole qui préparait un repas chez soi laisse une récupération sans repas à récupérer. L'annulation est acceptée,
 * la récupération reste (elle compte toujours une présence au dîner) et les coordinateurs préviennent la personne.
 * Pas de fenêtre de 7 jours, contrairement à {@link IncompleteEveningAlert} : quelqu'un viendrait chercher un repas qui
 * n'existe pas. Aucune donnée personnelle : la soirée seule.
 */
public final class PickupWithoutMealAlert {

    private PickupWithoutMealAlert() {
    }

    public static boolean triggeredBy(Evening before, Evening after) {
        return after.pickupsWithoutMeal() > before.pickupsWithoutMeal();
    }
}
