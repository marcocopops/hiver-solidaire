package com.marcantoinemeunier.hiversolidaire.benevole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Formulaire de première connexion (cahier des charges §6, étape 4), validé côté serveur. Aucun champ libre
 * (§7) : nom et prénom sont limités aux lettres et à la ponctuation des noms, le téléphone à un numéro français.
 */
public class ProfileForm {

    /** Lettres (accents compris), espaces, tirets, apostrophes et points ; commence par une lettre. */
    static final String NAME_PATTERN = "^\\s*\\p{L}[\\p{L}\\p{M} '’.\\-]*$";

    /** Numéro français : 0X XX XX XX XX ou +33 X XX XX XX XX, espaces, points et tirets tolérés. */
    static final String PHONE_PATTERN = "^\\s*(?:\\+33|0033|0)\\s*[1-9](?:[\\s.\\-]*\\d){8}\\s*$";

    @NotBlank(message = "{profile.error.lastName}")
    @Size(max = 100, message = "{profile.error.lastName}")
    @Pattern(regexp = NAME_PATTERN, message = "{profile.error.lastName}")
    private String lastName;

    @NotBlank(message = "{profile.error.firstName}")
    @Size(max = 100, message = "{profile.error.firstName}")
    @Pattern(regexp = NAME_PATTERN, message = "{profile.error.firstName}")
    private String firstName;

    @NotBlank(message = "{profile.error.phone}")
    @Size(max = 30, message = "{profile.error.phone}")
    @Pattern(regexp = PHONE_PATTERN, message = "{profile.error.phone}")
    private String phone;

    /** « Me recontacter la saison prochaine » : décoché par défaut. */
    private boolean recontactNextSeason;

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isRecontactNextSeason() {
        return recontactNextSeason;
    }

    public void setRecontactNextSeason(boolean recontactNextSeason) {
        this.recontactNextSeason = recontactNextSeason;
    }

    String normalizedLastName() {
        return collapseSpaces(lastName);
    }

    String normalizedFirstName() {
        return collapseSpaces(firstName);
    }

    /** Format national sans séparateur : 0612345678. */
    String normalizedPhone() {
        var digits = phone.replaceAll("[\\s.\\-]", "");
        if (digits.startsWith("+33")) {
            return "0" + digits.substring(3);
        }
        if (digits.startsWith("0033")) {
            return "0" + digits.substring(4);
        }
        return digits;
    }

    private static String collapseSpaces(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }
}
