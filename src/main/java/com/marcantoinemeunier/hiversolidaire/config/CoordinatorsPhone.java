package com.marcantoinemeunier.hiversolidaire.config;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Téléphone des coordinateurs, affiché quand le bénévole ne peut plus se désinscrire seul (R6). Il vient de la
 * configuration ({@code APP_COORDINATORS_PHONE}), jamais du dépôt ; S4b pourra le déplacer dans les réglages de la
 * saison.
 *
 * @param display numéro tel que configuré, pour l'affichage
 * @param telUri lien {@code tel:} (chiffres et « + » seulement)
 */
public record CoordinatorsPhone(String display, String telUri) {

    private static final Pattern DISPLAY = Pattern.compile("^\\+?[0-9][0-9 .\\-]{2,24}$");
    private static final Pattern DIALABLE = Pattern.compile("^\\+?[0-9]{4,15}$");

    /**
     * Numéro configuré, ou vide s'il n'est pas renseigné.
     *
     * @throws IllegalStateException si la valeur n'est pas un numéro de téléphone : l'appli ne démarre pas
     */
    public static Optional<CoordinatorsPhone> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var display = value.strip();
        var dialable = display.replaceAll("[ .\\-]", "");
        if (!DISPLAY.matcher(display).matches() || !DIALABLE.matcher(dialable).matches()) {
            throw new IllegalStateException("app.coordinators.phone is not a phone number");
        }
        return Optional.of(new CoordinatorsPhone(display, "tel:" + dialable));
    }
}
