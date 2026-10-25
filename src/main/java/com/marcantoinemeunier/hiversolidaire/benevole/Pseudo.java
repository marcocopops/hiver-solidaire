package com.marcantoinemeunier.hiversolidaire.benevole;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pseudo public d'un bénévole, affiché sur le planning (cahier des charges §4, §7). C'est un champ libre, donc
 * étroitement borné :
 * <ul>
 *   <li>3 à 20 caractères, après suppression des espaces autour et composition des accents (NFC) ;</li>
 *   <li>lettres latines (A-Z, accents de Latin-1, Latin étendu A : Œ, Ł…), chiffres 0-9, tiret, souligné ;</li>
 *   <li>commence par une lettre ou un chiffre.</li>
 * </ul>
 * Les autres alphabets sont refusés, lettres latines « pleine chasse » comprises : un « Маrie » au М cyrillique
 * ressemblerait trait pour trait à « Marie » (usurpation par homoglyphe). L'unicité ne tient pas compte de la casse.
 */
public final class Pseudo {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;

    private static final String LETTER = "A-Za-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u00FF\\u0100-\\u017F";
    private static final Pattern SHAPE =
            Pattern.compile("^[" + LETTER + "0-9][" + LETTER + "0-9_-]{" + (MIN_LENGTH - 1) + "," + (MAX_LENGTH - 1) + "}$");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^" + LETTER + "0-9_-]");
    private static final Pattern LEADING_SEPARATORS = Pattern.compile("^[_-]+");
    private static final String FALLBACK = "Benevole";

    private Pseudo() {
    }

    /** Pseudo normalisé, ou vide s'il ne respecte pas les règles. */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        var normalized = Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
        return SHAPE.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
    }

    /** Clé d'unicité, insensible à la casse (la base garantit la même règle avec {@code lower(pseudo)}). */
    public static String key(String pseudo) {
        return pseudo.toLowerCase(Locale.ROOT);
    }

    /**
     * Suggestion « prénom + initiale du nom » (« Marie Dupont » → « MarieD ») : caractères non autorisés retirés,
     * 20 caractères au plus, complétée par « 1 » si elle est trop courte. Toujours un pseudo valide. Le même calcul
     * est fait en JavaScript pendant la saisie ({@code static/js/profil.js}).
     */
    public static String suggest(String firstName, String lastName) {
        var first = clean(firstName);
        if (first.isEmpty()) {
            first = FALLBACK;
        }
        var last = clean(lastName);
        var initial = last.isEmpty() ? "" : last.substring(0, 1).toUpperCase(Locale.ROOT);
        if (first.length() + initial.length() > MAX_LENGTH) {
            first = first.substring(0, MAX_LENGTH - initial.length());
        }
        var suggestion = first + initial;
        while (suggestion.length() < MIN_LENGTH) {
            suggestion += "1";
        }
        return suggestion;
    }

    /** Variante numérotée d'un pseudo déjà pris (« MarieD » → « MarieD2 »), raccourcie pour tenir en 20 caractères. */
    public static String variant(String pseudo, int number) {
        var suffix = String.valueOf(number);
        var base = pseudo.length() + suffix.length() > MAX_LENGTH
                ? pseudo.substring(0, MAX_LENGTH - suffix.length())
                : pseudo;
        return base + suffix;
    }

    private static String clean(String name) {
        if (name == null) {
            return "";
        }
        var composed = Normalizer.normalize(name, Normalizer.Form.NFC);
        return LEADING_SEPARATORS.matcher(NOT_ALLOWED.matcher(composed).replaceAll("")).replaceAll("");
    }
}
