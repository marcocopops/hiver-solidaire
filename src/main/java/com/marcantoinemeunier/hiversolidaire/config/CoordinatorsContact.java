package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.benevole.EmailAddress;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Contact des coordinateurs, affiché quand le bénévole ne peut plus se désinscrire seul (R6) : un numéro de
 * téléphone français ou une adresse e-mail. Il vient seulement des réglages de la saison, jamais de la configuration
 * ni du dépôt. Ne jamais journaliser la valeur : une adresse est une donnée personnelle (CLAUDE.md).
 *
 * @param display valeur affichée : le numéro tel que saisi, ou l'adresse en minuscules
 * @param uri lien {@code tel:} (chiffres et « + » seulement) ou {@code mailto:} (adresse sans caractère réservé)
 */
public record CoordinatorsContact(Kind kind, String display, String uri) {

    public enum Kind {
        PHONE,
        EMAIL
    }

    /** Longueur maximale : celle d'une adresse e-mail, et de la colonne {@code season.coordinators_contact}. */
    public static final int MAX_LENGTH = EmailAddress.MAX_LENGTH;

    private static final Pattern PHONE_DISPLAY = Pattern.compile("^\\+?[0-9][0-9 .\\-]{2,24}$");
    /** Numéro français : 0 suivi de 9 chiffres (le premier de 1 à 9), ou la même chose avec l'indicatif +33. */
    private static final Pattern FRENCH_PHONE = Pattern.compile("^(0|\\+33)[1-9][0-9]{8}$");
    /**
     * Partie locale d'une adresse acceptée comme contact. Plus stricte qu'{@link EmailAddress} : {@code ?}, {@code &},
     * {@code %} ou {@code =} ajouteraient des en-têtes ou un encodage au lien {@code mailto:} (RFC 6068).
     */
    private static final Pattern MAILTO_SAFE_LOCAL_PART = Pattern.compile("^[a-z0-9._+-]+$");

    /**
     * Contact saisi, ou vide s'il n'est pas renseigné.
     *
     * @throws IllegalArgumentException si la valeur n'est ni un numéro français ni une adresse e-mail ; le message ne
     *     contient jamais la valeur
     */
    public static Optional<CoordinatorsContact> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var stripped = value.strip();
        if (stripped.contains("@")) {
            return Optional.of(email(stripped));
        }
        var dialable = stripped.replaceAll("[ .\\-]", "");
        if (!PHONE_DISPLAY.matcher(stripped).matches() || !FRENCH_PHONE.matcher(dialable).matches()) {
            throw invalid();
        }
        return Optional.of(new CoordinatorsContact(Kind.PHONE, stripped, "tel:" + dialable));
    }

    /**
     * Contact enregistré, relu à l'affichage : vide s'il n'est pas renseigné ou s'il est illisible (il est validé à la
     * saisie ; une valeur écrite hors de l'appli ne produit jamais de lien).
     */
    public static Optional<CoordinatorsContact> readable(String value) {
        try {
            return parse(value);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static CoordinatorsContact email(String value) {
        var address = EmailAddress.normalize(value).orElseThrow(CoordinatorsContact::invalid);
        if (!MAILTO_SAFE_LOCAL_PART.matcher(address.substring(0, address.indexOf('@'))).matches()) {
            throw invalid();
        }
        return new CoordinatorsContact(Kind.EMAIL, address, "mailto:" + address);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Coordinators contact is neither a French phone number nor an email address");
    }
}
