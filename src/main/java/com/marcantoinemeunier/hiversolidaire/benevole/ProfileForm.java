package com.marcantoinemeunier.hiversolidaire.benevole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Formulaire de première connexion (cahier des charges §6, étape 4), validé côté serveur. Aucun champ libre
 * (§7) : nom et prénom sont limités aux lettres et à la ponctuation des noms ; le téléphone suit les règles de
 * {@link PhoneFields}.
 */
public class ProfileForm extends PhoneFields {

    /** Lettres (accents compris), espaces, tirets, apostrophes et points ; commence par une lettre. */
    static final String NAME_PATTERN = "^\\s*\\p{L}[\\p{L}\\p{M} '’.\\-]*$";

    @NotBlank(message = "{profile.error.lastName}")
    @Size(max = 100, message = "{profile.error.lastName}")
    @Pattern(regexp = NAME_PATTERN, message = "{profile.error.lastName}")
    private String lastName;

    @NotBlank(message = "{profile.error.firstName}")
    @Size(max = 100, message = "{profile.error.firstName}")
    @Pattern(regexp = NAME_PATTERN, message = "{profile.error.firstName}")
    private String firstName;

    /** Pseudo public ; validé par {@link Pseudo#normalize(String)}. Vide : le serveur propose une suggestion. */
    private String pseudo;

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

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
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

    private static String collapseSpaces(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }
}
