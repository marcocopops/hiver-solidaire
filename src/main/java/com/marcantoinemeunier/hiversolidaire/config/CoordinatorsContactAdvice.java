package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose aux pages le contact des coordinateurs : celui des réglages de la saison courante, sinon
 * {@code APP_COORDINATORS_CONTACT}, sinon l'ancienne {@code APP_COORDINATORS_PHONE} (lue le temps de mettre à jour le
 * {@code .env} du serveur), sinon rien (message générique).
 */
@ControllerAdvice
class CoordinatorsContactAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(CoordinatorsContactAdvice.class);

    private final CoordinatorsContact fallback;
    private final SeasonSettingsService seasons;

    CoordinatorsContactAdvice(@Value("${app.coordinators.contact:}") String contact,
            @Value("${app.coordinators.phone:}") String legacyPhone, SeasonSettingsService seasons) {
        this.fallback = fallback(contact, legacyPhone);
        this.seasons = seasons;
    }

    /**
     * Contact de la configuration du serveur, ou {@code null} s'il n'y en a aucun. La valeur n'est jamais journalisée.
     *
     * @throws IllegalStateException si la valeur n'est ni un numéro français ni une adresse : l'appli ne démarre pas
     */
    static CoordinatorsContact fallback(String contact, String legacyPhone) {
        if (contact != null && !contact.isBlank()) {
            return parseConfigured(contact, "app.coordinators.contact");
        }
        if (legacyPhone != null && !legacyPhone.isBlank()) {
            LOG.warn("APP_COORDINATORS_PHONE is deprecated: rename it to APP_COORDINATORS_CONTACT in the .env file");
            return parseConfigured(legacyPhone, "app.coordinators.phone");
        }
        return null;
    }

    private static CoordinatorsContact parseConfigured(String value, String property) {
        try {
            return CoordinatorsContact.parse(value).orElseThrow();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(property + " is neither a French phone number nor an email address");
        }
    }

    @ModelAttribute("coordinatorsContact")
    CoordinatorsContact coordinatorsContact() {
        return seasons.current().map(SeasonSettings::coordinatorsContact)
                .flatMap(CoordinatorsContactAdvice::parseQuietly)
                .orElse(fallback);
    }

    /** Le contact de la saison est validé à la saisie ; une valeur illisible retombe sur la configuration. */
    private static Optional<CoordinatorsContact> parseQuietly(String value) {
        try {
            return CoordinatorsContact.parse(value);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
