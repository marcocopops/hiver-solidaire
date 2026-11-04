package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose aux pages le téléphone des coordinateurs : celui des réglages de la saison courante (S4b), sinon
 * {@code APP_COORDINATORS_PHONE}, sinon rien (message générique).
 */
@ControllerAdvice
class CoordinatorsPhoneAdvice {

    private final CoordinatorsPhone fallback;
    private final SeasonSettingsService seasons;

    CoordinatorsPhoneAdvice(@Value("${app.coordinators.phone:}") String phone, SeasonSettingsService seasons) {
        this.fallback = CoordinatorsPhone.parse(phone).orElse(null);
        this.seasons = seasons;
    }

    @ModelAttribute("coordinatorsPhone")
    CoordinatorsPhone coordinatorsPhone() {
        return seasons.current().map(SeasonSettings::coordinatorsPhone)
                .flatMap(CoordinatorsPhoneAdvice::parseQuietly)
                .orElse(fallback);
    }

    /** Le numéro de la saison est validé à la saisie ; une valeur illisible retombe sur la configuration. */
    private static java.util.Optional<CoordinatorsPhone> parseQuietly(String value) {
        try {
            return CoordinatorsPhone.parse(value);
        } catch (IllegalStateException e) {
            return java.util.Optional.empty();
        }
    }
}
