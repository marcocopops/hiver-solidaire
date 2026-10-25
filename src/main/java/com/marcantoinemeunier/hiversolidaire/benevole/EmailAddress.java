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

    // Volontairement simple : une partie locale, un @, un domaine avec au moins un point, sans espace.
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]{1,64}@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private EmailAddress() {
    }

    /** Adresse normalisée, ou vide si la saisie n'a pas la forme d'une adresse. */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        var normalized = input.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !SHAPE.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }
}
