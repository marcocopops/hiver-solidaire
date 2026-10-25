package com.marcantoinemeunier.hiversolidaire.benevole;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Adresse e-mail normalisée : espaces retirés, minuscules sur toute l'adresse. Les points et les « + » ne sont
 * pas retirés : chaque variante reste une adresse distincte. Ne jamais journaliser une adresse (CLAUDE.md).
 */
public final class EmailAddress {

    /** Longueur maximale d'une adresse (RFC 5321) et de la colonne {@code volunteer.email}. */
    public static final int MAX_LENGTH = 254;

    // Partie locale en « dot-atom » (RFC 5322, sans guillemets ni commentaires) et nom de domaine classique :
    // l'adresse enregistrée est exactement celle qui reçoit le lien, jamais une forme « Nom <adresse> » ni une liste.
    private static final Pattern SHAPE = Pattern.compile(
            "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private EmailAddress() {
    }

    /** Adresse normalisée, ou vide si la saisie n'a pas la forme d'une adresse. */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        var normalized = input.strip().toLowerCase(Locale.ROOT);
        var at = normalized.indexOf('@');
        if (normalized.length() > MAX_LENGTH || at > 64 || !SHAPE.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }
}
