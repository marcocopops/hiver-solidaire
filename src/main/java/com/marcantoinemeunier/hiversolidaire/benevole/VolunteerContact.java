package com.marcantoinemeunier.hiversolidaire.benevole;

import java.time.Instant;
import java.util.UUID;

/**
 * Coordonnées d'un bénévole, pour le back-office seulement (cahier des charges §2 : seuls les coordinateurs les voient).
 * Jamais journalisées, jamais rendues sur une page publique.
 *
 * @param email nul pour un bénévole inscrit par téléphone (sans e-mail, il ne peut pas se connecter)
 * @param reviewed un coordinateur l'a déjà vu : il n'est plus signalé comme nouveau
 */
public record VolunteerContact(UUID id, String lastName, String firstName, String pseudo, String phone, String email,
        Instant createdAt, boolean reviewed) {
}
