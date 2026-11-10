package com.marcantoinemeunier.hiversolidaire.notification;

import com.marcantoinemeunier.hiversolidaire.config.CoordinatorsContact;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettings;
import com.marcantoinemeunier.hiversolidaire.planning.season.SeasonSettingsService;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ce que les e-mails ont en commun : des liens absolus vers {@code APP_BASE_URL}, sans paramètre de suivi (§6, décision
 * 6 de S5a), et le contact des coordinateurs de la saison, seule source de l'en-tête « Répondre à ».
 */
@Component
class MailCommons {

    private final String baseUrl;
    private final SeasonSettingsService seasons;

    MailCommons(@Value("${app.base-url}") String baseUrl, SeasonSettingsService seasons) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.seasons = seasons;
    }

    /** Lien absolu vers une page de l'appli ; {@code path} commence par « / ». */
    String link(String path) {
        return baseUrl + path;
    }

    Optional<SeasonSettings> season() {
        return seasons.current();
    }

    /** Contact des coordinateurs de la saison courante, vide s'il n'est pas renseigné (ou illisible). */
    Optional<CoordinatorsContact> coordinatorsContact() {
        return season().map(SeasonSettings::coordinatorsContact).flatMap(CoordinatorsContact::readable);
    }

    /** « Répondre à » : le contact des coordinateurs quand c'est une adresse e-mail, sinon aucun en-tête. */
    static String replyTo(Optional<CoordinatorsContact> contact) {
        return contact.filter(c -> c.kind() == CoordinatorsContact.Kind.EMAIL).map(CoordinatorsContact::display)
                .orElse(null);
    }
}
