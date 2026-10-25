package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Une soirée : du dîner le soir J au petit-déjeuner le matin J+1. */
public final class Evening {

    private final LocalDate date;
    private final boolean closed;
    private final Needs needs;
    private final List<Registration> registrations;
    private final Map<Post, Integer> occupied = new EnumMap<>(Post.class);

    public Evening(LocalDate date, boolean closed, Needs needs, List<Registration> registrations) {
        this.date = Objects.requireNonNull(date, "date");
        this.closed = closed;
        this.needs = Objects.requireNonNull(needs, "needs");
        this.registrations = List.copyOf(registrations);
        for (Post post : Post.values()) {
            occupied.put(post, 0);
        }
        for (Registration registration : this.registrations) {
            for (Post post : registration.coveredPosts()) {
                occupied.merge(post, 1, Integer::sum);
            }
        }
    }

    public LocalDate date() {
        return date;
    }

    public List<Registration> registrations() {
        return registrations;
    }

    /** Fermée par un coordinateur (R7). */
    public boolean closed() {
        return closed;
    }

    public int occupied(Post post) {
        return occupied.get(post);
    }

    /** R1 : places restantes sur un poste, jamais négatives. */
    public int remaining(Post post) {
        return Math.max(0, needs.of(post).maximum() - occupied(post));
    }

    /** R1 : un type n'est disponible que s'il reste de la place sur chaque poste qu'il couvre. */
    public boolean isAvailable(RegistrationType type, DishChoice dishChoice) {
        return type.coveredPosts(dishChoice).stream().allMatch(post -> remaining(post) > 0);
    }

    /**
     * Contrôle d'une nouvelle inscription (R7, puis R2, R3 et R1). R4 n'empêche jamais une inscription : une
     * préparation chez soi sans récupération est acceptée, et {@link #needsPickupAlert()} le signale.
     */
    public RegistrationDecision decide(Season season, Registration candidate, Instant now) {
        if (candidate.volunteerId() == null) {
            throw new IllegalArgumentException("A new registration needs a volunteer");
        }
        if (now.isBefore(season.registrationsOpenAt())) {
            return RegistrationDecision.NOT_OPEN_YET;
        }
        if (!season.contains(date)) {
            return RegistrationDecision.OUT_OF_SEASON;
        }
        if (closed) {
            return RegistrationDecision.CLOSED;
        }
        if (isPast(ParisTime.today(now))) {
            return RegistrationDecision.PAST;
        }
        if (registrations.stream().anyMatch(existing -> candidate.volunteerId().equals(existing.volunteerId()))) {
            return RegistrationDecision.ALREADY_REGISTERED;
        }
        if (candidate.dishChoice() != null && remaining(candidate.dishChoice().post()) == 0) {
            return RegistrationDecision.DISH_TAKEN;
        }
        if (!isAvailable(candidate.type(), candidate.dishChoice())) {
            return RegistrationDecision.FULL;
        }
        return RegistrationDecision.ACCEPTED;
    }

    /** R4 : préparations chez soi qui n'ont pas de récupération le même soir (une récupération par préparation). */
    public int unmatchedHomePreparations() {
        return Math.max(0, count(RegistrationType.PREPARE_AT_HOME) - count(RegistrationType.PICK_UP_AND_SHARE));
    }

    /** R4 : les coordinateurs doivent organiser la remise d'un repas. */
    public boolean needsPickupAlert() {
        return unmatchedHomePreparations() > 0;
    }

    /** R5 : postes dont le minimum n'est pas couvert, dans l'ordre d'affichage. */
    public List<Post> missingPosts() {
        var missing = new ArrayList<Post>();
        for (Post post : Post.values()) {
            if (occupied(post) < needs.of(post).minimum()) {
                missing.add(post);
            }
        }
        return missing;
    }

    /** R5 : complète, partielle ou vide. */
    public EveningStatus status() {
        if (missingPosts().isEmpty()) {
            return EveningStatus.COMPLETE;
        }
        return registrations.isEmpty() ? EveningStatus.EMPTY : EveningStatus.PARTIAL;
    }

    /** R7 : une soirée est passée à partir de minuit le lendemain, heure de Paris. */
    public boolean isPast(LocalDate today) {
        return date.isBefore(today);
    }

    private int count(RegistrationType type) {
        return (int) registrations.stream().filter(registration -> registration.type() == type).count();
    }
}
