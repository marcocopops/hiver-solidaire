package com.marcantoinemeunier.hiversolidaire.planning.season;

import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Réglages d'une saison (cahier des charges §5, §6 « Saison »).
 *
 * @param registrationsOpenAt ouverture des inscriptions, heure de Paris
 * @param needs besoins et horaires, dans l'ordre d'affichage d'une journée
 * @param venueAddress adresse du lieu : jamais sur la page publique (§6)
 * @param coordinatorsPhone téléphone des coordinateurs saisi pour la saison, ou {@code null}
 */
public record SeasonSettings(UUID id, String label, LocalDate firstEvening, LocalDate lastEvening,
        LocalDateTime registrationsOpenAt, boolean sample, List<NeedSetting> needs, String venueAddress,
        String instructions, String coordinatorsPhone) {

    public record NeedSetting(Post post, int minimum, int maximum, String schedule) {
    }
}
