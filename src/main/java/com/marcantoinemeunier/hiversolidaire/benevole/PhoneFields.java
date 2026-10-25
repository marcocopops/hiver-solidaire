package com.marcantoinemeunier.hiversolidaire.benevole;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Téléphone d'un bénévole, validé côté serveur : un indicatif (+33 par défaut) et un numéro national, enregistrés
 * ensemble au format international E.164 ({@code +33612345678}). Mêmes règles au premier profil
 * ({@link ProfileForm}) et dans « Mon compte » ({@link PhoneForm}).
 */
public abstract class PhoneFields {

    /** Indicatif : +33, 0033 ou 33 ; un à trois chiffres, sans zéro en tête. */
    static final String COUNTRY_CODE_PATTERN = "^\\s*(?:\\+\\s*|00)?[1-9]\\d{0,2}\\s*$";

    /** Numéro national, sans indicatif : chiffres, espaces, points et tirets. */
    static final String PHONE_PATTERN = "^\\s*\\d(?:[\\s.\\-]*\\d)*\\s*$";

    static final String FRANCE = "33";

    /** L'Italie garde le 0 initial dans le numéro international : c'est le seul « 0 » qu'on ne retire pas. */
    static final String ITALY = "39";

    /** Un numéro E.164 compte au plus 15 chiffres, indicatif compris. */
    private static final int E164_MAX_DIGITS = 15;
    private static final int MIN_NATIONAL_DIGITS = 4;

    @NotBlank(message = "{profile.error.phoneCountryCode}")
    @Size(max = 6, message = "{profile.error.phoneCountryCode}")
    @Pattern(regexp = COUNTRY_CODE_PATTERN, message = "{profile.error.phoneCountryCode}")
    private String phoneCountryCode = "+" + FRANCE;

    @NotBlank(message = "{profile.error.phone}")
    @Size(max = 30, message = "{profile.error.phone}")
    @Pattern(regexp = PHONE_PATTERN, message = "{profile.error.phone}")
    private String phone;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhoneCountryCode() {
        return phoneCountryCode;
    }

    public void setPhoneCountryCode(String phoneCountryCode) {
        this.phoneCountryCode = phoneCountryCode;
    }

    /**
     * Le numéro complet est plausible : en France, 9 chiffres après le 0 ; ailleurs, au moins 4 chiffres nationaux
     * et 15 chiffres au plus avec l'indicatif (E.164). Les formats propres à chaque pays ne sont pas vérifiés.
     */
    @AssertTrue(message = "{profile.error.phone}")
    public boolean isPhoneValid() {
        var countryCode = countryCodeDigits();
        var national = nationalDigits();
        if (countryCode == null || national == null) {
            // Champ vide ou mal formé : signalé par les contraintes du champ lui-même.
            return true;
        }
        if (FRANCE.equals(countryCode)) {
            return national.length() == 9 && national.charAt(0) != '0';
        }
        return national.length() >= MIN_NATIONAL_DIGITS
                && countryCode.length() + national.length() <= E164_MAX_DIGITS;
    }

    /** Format international E.164 : {@code +33612345678}. À n'appeler qu'après validation. */
    String normalizedPhone() {
        return "+" + countryCodeDigits() + nationalDigits();
    }

    /** Chiffres de l'indicatif, sans « + » ni « 00 », ou {@code null} s'il est mal formé. */
    private String countryCodeDigits() {
        if (phoneCountryCode == null || !phoneCountryCode.matches(COUNTRY_CODE_PATTERN)) {
            return null;
        }
        var digits = phoneCountryCode.replaceAll("[^\\d]", "");
        return digits.startsWith("00") ? digits.substring(2) : digits;
    }

    /** Chiffres du numéro national, sans le 0 initial (sauf en Italie), ou {@code null} s'il est mal formé. */
    private String nationalDigits() {
        if (phone == null || !phone.matches(PHONE_PATTERN)) {
            return null;
        }
        var digits = phone.replaceAll("[^\\d]", "");
        if (digits.startsWith("0") && !ITALY.equals(countryCodeDigits())) {
            digits = digits.substring(1);
        }
        return digits;
    }
}
