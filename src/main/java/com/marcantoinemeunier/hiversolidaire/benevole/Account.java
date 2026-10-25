package com.marcantoinemeunier.hiversolidaire.benevole;

/** Données du bénévole connecté, pour « Mon compte » : les siennes seulement, jamais journalisées. */
public record Account(String email, String lastName, String firstName, String pseudo, String phone,
        boolean recontactNextSeason) {
}
