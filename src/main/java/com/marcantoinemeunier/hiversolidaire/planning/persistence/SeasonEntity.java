package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import com.marcantoinemeunier.hiversolidaire.planning.domain.Needs;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Season;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "season")
public class SeasonEntity {

    @Id
    private UUID id;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "first_evening", nullable = false)
    private LocalDate firstEvening;

    @Column(name = "last_evening", nullable = false)
    private LocalDate lastEvening;

    @Column(name = "registrations_open_at", nullable = false)
    private Instant registrationsOpenAt;

    @Column(name = "sample", nullable = false)
    private boolean sample;

    @ElementCollection
    @CollectionTable(name = "season_need", joinColumns = @JoinColumn(name = "season_id"))
    private List<NeedEmbeddable> needs = new ArrayList<>();

    protected SeasonEntity() {
    }

    public UUID id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Saison d'exemple, à signaler comme telle à l'affichage. */
    public boolean sample() {
        return sample;
    }

    public Season toDomain() {
        return new Season(firstEvening, lastEvening, registrationsOpenAt);
    }

    public Needs needs() {
        return Needs.of(needs.stream().map(NeedEmbeddable::toDomain).toList());
    }
}
