package com.marcantoinemeunier.hiversolidaire.benevole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Bénévole (cahier des charges §7). Aucun champ libre ; aucune de ces valeurs ne va dans les logs. */
@Entity
@Table(name = "volunteer")
public class VolunteerEntity {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "recontact_next_season", nullable = false)
    private boolean recontactNextSeason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "profile_completed_at")
    private Instant profileCompletedAt;

    protected VolunteerEntity() {
    }

    public UUID id() {
        return id;
    }

    public String firstName() {
        return firstName;
    }

    /** Vrai une fois le formulaire de première connexion rempli (§6, étape 4). */
    public boolean profileCompleted() {
        return profileCompletedAt != null;
    }

    void completeProfile(String lastName, String firstName, String phone, boolean recontactNextSeason,
            Instant now) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.phone = phone;
        this.recontactNextSeason = recontactNextSeason;
        this.profileCompletedAt = now;
        this.updatedAt = now;
    }
}
