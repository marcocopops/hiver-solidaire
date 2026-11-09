package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Une soirée : du dîner le soir J au petit-déjeuner le matin J+1. */
public final class Evening {

    private final LocalDate date;
    private final Needs needs;
    private final List<Registration> registrations;
    private final Map<Post, Integer> occupied = new EnumMap<>(Post.class);

    public Evening(LocalDate date, Needs needs, List<Registration> registrations) {
        this.date = Objects.requireNonNull(date, "date");
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
     * Contrôle d'une nouvelle inscription : R7, puis R2 (un poste par bénévole, « Récupérer » seulement avec le
     * petit-déjeuner), R3, R4 et R1. Pour R4, une récupération n'est acceptée que si un repas préparé chez soi attend
     * encore la sienne ; une préparation chez soi sans récupération reste acceptée, et {@link #needsPickupAlert()} le
     * signale.
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
        if (isPast(ParisTime.today(now))) {
            return RegistrationDecision.PAST;
        }
        var mine = registrations.stream()
                .filter(existing -> candidate.volunteerId().equals(existing.volunteerId()))
                .toList();
        if (mine.stream().anyMatch(existing -> existing.ownPost() == candidate.ownPost())) {
            return RegistrationDecision.ALREADY_REGISTERED;
        }
        if (mine.stream().anyMatch(candidate::conflictsWith)) {
            return RegistrationDecision.NOT_CUMULABLE;
        }
        if (candidate.dishChoice() != null && remaining(candidate.dishChoice().post()) == 0) {
            return RegistrationDecision.DISH_TAKEN;
        }
        if (candidate.type() == RegistrationType.PICK_UP_AND_SHARE && unmatchedHomePreparations() == 0) {
            return RegistrationDecision.NOTHING_TO_PICK_UP;
        }
        if (!isAvailable(candidate.type(), candidate.dishChoice())) {
            return RegistrationDecision.FULL;
        }
        return RegistrationDecision.ACCEPTED;
    }

    /**
     * R6 : contrôle de la désinscription d'une inscription d'un bénévole ({@code registration} porte son identifiant).
     */
    public CancellationDecision decideCancellation(Registration registration, Instant now) {
        if (registration.volunteerId() == null || !registrations.contains(registration)) {
            return CancellationDecision.NOT_REGISTERED;
        }
        if (!Cancellation.allowed(date, now)) {
            return CancellationDecision.TOO_LATE;
        }
        return CancellationDecision.ACCEPTED;
    }

    /**
     * La même soirée sans aucune inscription du bénévole (suppression de son compte) : R1 à R5 sont recalculés sans
     * elles.
     */
    public Evening without(UUID volunteerId) {
        return new Evening(date, needs, registrations.stream()
                .filter(registration -> !volunteerId.equals(registration.volunteerId()))
                .toList());
    }

    /**
     * La même soirée sans cette inscription (désinscription d'un poste) : les autres postes du même bénévole restent.
     * Deux inscriptions actives égales n'existent pas (R2), une seule est donc retirée.
     */
    public Evening without(Registration registration) {
        var remaining = new ArrayList<>(registrations);
        remaining.remove(registration);
        return new Evening(date, needs, remaining);
    }

    /** Postes qui couvraient leur minimum ici et ne le couvrent plus dans {@code after}. */
    public List<Post> lostMinimums(Evening after) {
        var lost = new ArrayList<Post>();
        for (Post post : Post.values()) {
            var minimum = needs.of(post).minimum();
            if (occupied(post) >= minimum && after.occupied(post) < minimum) {
                lost.add(post);
            }
        }
        return lost;
    }

    /** R4 : préparations chez soi qui n'ont pas de récupération le même soir (une récupération par préparation). */
    public int unmatchedHomePreparations() {
        return Math.max(0, count(RegistrationType.PREPARE_AT_HOME) - count(RegistrationType.PICK_UP_AND_SHARE));
    }

    /** R4 : les coordinateurs doivent organiser la remise d'un repas. */
    public boolean needsPickupAlert() {
        return unmatchedHomePreparations() > 0;
    }

    /**
     * R4 : récupérations sans repas préparé chez soi à récupérer. Une récupération n'est acceptée que pour un repas qui
     * l'attend ; il n'en reste sans repas qu'après la désinscription (ou le déplacement) d'une préparation chez soi déjà
     * récupérée, et les coordinateurs en sont alertés ({@link PickupWithoutMealAlert}).
     */
    public int pickupsWithoutMeal() {
        return Math.max(0, count(RegistrationType.PICK_UP_AND_SHARE) - count(RegistrationType.PREPARE_AT_HOME));
    }

    /** R5 : places manquantes sur un poste pour atteindre son minimum. */
    public int missing(Post post) {
        return Math.max(0, needs.of(post).minimum() - occupied(post));
    }

    /** R5 : postes dont le minimum n'est pas couvert, dans l'ordre de {@link Post}. */
    public List<Post> missingPosts() {
        var missing = new ArrayList<Post>();
        for (Post post : Post.values()) {
            if (missing(post) > 0) {
                missing.add(post);
            }
        }
        return missing;
    }

    /** R7 : une soirée est passée à partir de minuit le lendemain, heure de Paris. */
    public boolean isPast(LocalDate today) {
        return date.isBefore(today);
    }

    private int count(RegistrationType type) {
        return (int) registrations.stream().filter(registration -> registration.type() == type).count();
    }
}
