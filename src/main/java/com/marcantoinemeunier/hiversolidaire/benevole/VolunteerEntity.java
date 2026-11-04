package com.marcantoinemeunier.hiversolidaire.benevole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Bénévole (cahier des charges §7). Aucun champ libre ; aucune de ces valeurs ne va dans les logs. Un compte
 * supprimé reste, anonyme, pour que ses inscriptions passées restent comptées (ADR-0008).
 */
@Entity
@Table(name = "volunteer")
public class VolunteerEntity {

    @Id
    private UUID id;

    /** Nul seulement pour un compte supprimé (contrainte volunteer_deleted_is_anonymous). */
    @Column(name = "email")
    private String email;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "phone")
    private String phone;

    /** Pseudo public, affiché sur le planning ; unique sans tenir compte de la casse. */
    @Column(name = "pseudo")
    private String pseudo;

    @Column(name = "recontact_next_season", nullable = false)
    private boolean recontactNextSeason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "profile_completed_at")
    private Instant profileCompletedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Vu par un coordinateur : n'est plus signalé comme nouveau bénévole (§2). */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected VolunteerEntity() {
    }

    /**
     * Bénévole inscrit par téléphone par un coordinateur (S4b) : profil complet, sans e-mail, donc sans connexion
     * possible. Vu d'office : c'est un coordinateur qui l'a créé.
     */
    static VolunteerEntity withoutEmail(String lastName, String firstName, String pseudo, String phone, Instant now) {
        var volunteer = new VolunteerEntity();
        volunteer.id = UUID.randomUUID();
        volunteer.createdAt = now;
        volunteer.completeProfile(lastName, firstName, pseudo, phone, false, now);
        volunteer.reviewedAt = now;
        return volunteer;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String firstName() {
        return firstName;
    }

    Account toAccount() {
        return new Account(email, lastName, firstName, pseudo, phone, recontactNextSeason);
    }

    VolunteerContact toContact() {
        return new VolunteerContact(id, lastName, firstName, pseudo, phone, email, createdAt, reviewedAt != null);
    }

    public boolean deleted() {
        return deletedAt != null;
    }

    void markReviewed(Instant now) {
        if (reviewedAt == null) {
            this.reviewedAt = now;
        }
    }

    /** Vrai une fois le formulaire de première connexion rempli (§6, étape 4). */
    public boolean profileCompleted() {
        return profileCompletedAt != null;
    }

    void completeProfile(String lastName, String firstName, String pseudo, String phone,
            boolean recontactNextSeason, Instant now) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.pseudo = pseudo;
        this.phone = phone;
        this.recontactNextSeason = recontactNextSeason;
        this.profileCompletedAt = now;
        this.updatedAt = now;
    }

    /** Correction par un coordinateur (§6 « Mon compte » : nom et prénom se corrigent auprès d'un coordinateur). */
    void correctIdentity(String lastName, String firstName, String pseudo, Instant now) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.pseudo = pseudo;
        this.updatedAt = now;
    }

    void changePhone(String phone, Instant now) {
        this.phone = phone;
        this.updatedAt = now;
    }

    void changePseudo(String pseudo, Instant now) {
        this.pseudo = pseudo;
        this.updatedAt = now;
    }

    void changeRecontact(boolean recontactNextSeason, Instant now) {
        this.recontactNextSeason = recontactNextSeason;
        this.updatedAt = now;
    }

    /**
     * Suppression du compte : plus aucune donnée personnelle. Sans e-mail, le compte ne peut plus être retrouvé à la
     * connexion ; une nouvelle connexion avec la même adresse crée un compte neuf.
     */
    void anonymize(Instant now) {
        this.email = null;
        this.lastName = null;
        this.firstName = null;
        this.phone = null;
        this.pseudo = null;
        this.recontactNextSeason = false;
        this.profileCompletedAt = null;
        this.deletedAt = now;
        this.updatedAt = now;
    }
}
