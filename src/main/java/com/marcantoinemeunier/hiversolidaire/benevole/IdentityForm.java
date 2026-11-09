package com.marcantoinemeunier.hiversolidaire.benevole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Back-office : correction du nom, du prénom et du pseudo d'un bénévole par un coordinateur. Mêmes règles qu'au premier
 * profil ({@link ProfileForm}) ; le pseudo est validé par {@link Pseudo#normalize(String)}.
 */
public class IdentityForm {

    @NotBlank(message = "{profile.error.lastName}")
    @Size(max = 100, message = "{profile.error.lastName}")
    @Pattern(regexp = ProfileForm.NAME_PATTERN, message = "{profile.error.lastName}")
    private String lastName;

    @NotBlank(message = "{profile.error.firstName}")
    @Size(max = 100, message = "{profile.error.firstName}")
    @Pattern(regexp = ProfileForm.NAME_PATTERN, message = "{profile.error.firstName}")
    private String firstName;

    private String pseudo;

    public static IdentityForm of(VolunteerContact contact) {
        var form = new IdentityForm();
        form.lastName = contact.lastName();
        form.firstName = contact.firstName();
        form.pseudo = contact.pseudo();
        return form;
    }

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

    String normalizedLastName() {
        return lastName.strip().replaceAll("\\s+", " ");
    }

    String normalizedFirstName() {
        return firstName.strip().replaceAll("\\s+", " ");
    }
}
