package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose aux pages le contact des coordinateurs, saisi dans les réglages de la saison courante. Il n'y a pas de repli
 * par la configuration du serveur : sans contact, les pages affichent « Contactez un coordinateur. », sans lien, et
 * le tableau de bord du back-office le signale.
 */
@ControllerAdvice
class CoordinatorsContactAdvice {

    private final SeasonSettingsService seasons;

    CoordinatorsContactAdvice(SeasonSettingsService seasons) {
        this.seasons = seasons;
    }

    @ModelAttribute("coordinatorsContact")
    CoordinatorsContact coordinatorsContact() {
        return seasons.current().map(SeasonSettings::coordinatorsContact)
                .flatMap(CoordinatorsContact::readable)
                .orElse(null);
    }
}
