package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.util.Set;
import java.util.UUID;

/**
 * Pseudo public d'un inscrit et ce qu'il couvre, pour le planning. Aucune autre donnée du bénévole.
 * {@code volunteerId} sert seulement côté serveur à remplacer son propre pseudo par « Vous » ; il n'est jamais rendu.
 */
public record RegistrantPseudo(UUID eveningId, UUID volunteerId, RegistrationType type, DishChoice dishChoice,
        String pseudo) {

    /** Postes couverts : « préparer et partager » compte au plat et aux présences, une récupération aux présences. */
    public Set<Post> coveredPosts() {
        return type.coveredPosts(dishChoice);
    }
}
