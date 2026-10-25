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

/** Lecture seule en S2 : aucune route ne crée d'inscription avant S3. */
@Entity
@Table(name = "registration")
public class RegistrationEntity {

    @Id
    private UUID id;

    @Column(name = "evening_id", nullable = false)
    private UUID eveningId;

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

    public UUID eveningId() {
        return eveningId;
    }

    public Registration toDomain() {
        return Registration.of(type, dishChoice);
    }
}
