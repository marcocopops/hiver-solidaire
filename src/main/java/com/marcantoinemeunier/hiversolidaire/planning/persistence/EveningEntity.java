package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Needs;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "evening")
public class EveningEntity {

    @Id
    private UUID id;

    @Column(name = "season_id", nullable = false)
    private UUID seasonId;

    @Column(name = "evening_date", nullable = false)
    private LocalDate date;

    protected EveningEntity() {
    }

    public UUID id() {
        return id;
    }

    public UUID seasonId() {
        return seasonId;
    }

    public LocalDate date() {
        return date;
    }

    public Evening toDomain(Needs needs, List<Registration> registrations) {
        return new Evening(date, needs, registrations);
    }
}
