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
 * @param coordinatorsContact contact des coordinateurs saisi pour la saison (téléphone ou adresse e-mail), ou
 *     {@code null}
 */
public record SeasonSettings(UUID id, LocalDate firstEvening, LocalDate lastEvening,
        LocalDateTime registrationsOpenAt, boolean sample, List<NeedSetting> needs, String coordinatorsContact) {

    public record NeedSetting(Post post, int minimum, int maximum, String schedule) {
    }
}
