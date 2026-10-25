package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Inscription d'un bénévole sur une soirée. Créée seulement par l'inscription, sous le verrou de R1. */
@Entity
@Table(name = "registration")
public class RegistrationEntity {

    @Id
    private UUID id;

    @Column(name = "evening_id", nullable = false)
    private UUID eveningId;

    @Column(name = "volunteer_id", nullable = false)
    private UUID volunteerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RegistrationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "dish_choice")
    private DishChoice dishChoice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RegistrationEntity() {
    }

    public RegistrationEntity(UUID eveningId, Registration registration, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.eveningId = eveningId;
        this.volunteerId = registration.volunteerId();
        this.type = registration.type();
        this.dishChoice = registration.dishChoice();
        this.createdAt = createdAt;
    }

    public UUID eveningId() {
        return eveningId;
    }

    public UUID volunteerId() {
        return volunteerId;
    }

    public Registration toDomain() {
        return new Registration(type, dishChoice, volunteerId);
    }
}
