package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Inscription active et coordonnées de son bénévole, pour le back-office seulement (cahier des charges §2, §6 « Fiche
 * soirée ») : jamais sur une page publique, jamais dans les logs.
 *
 * @param eveningDate date du soir J de la soirée qui porte l'inscription
 * @param email nul pour un bénévole inscrit par téléphone
 */
public record RegistrationContact(UUID registrationId, UUID eveningId, LocalDate eveningDate, RegistrationType type,
        DishChoice dishChoice, UUID volunteerId, String lastName, String firstName, String pseudo, String phone,
        String email) {

    /** Jour de l'activité (ADR-0009) : le lendemain matin pour un petit-déjeuner. */
    public LocalDate activityDate() {
        return type.activityDate(eveningDate);
    }
}
