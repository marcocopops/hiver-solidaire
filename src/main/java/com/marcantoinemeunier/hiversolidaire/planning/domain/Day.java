package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Une journée du planning (ADR-0009) : l'affichage regroupe par jour calendaire, les données restent par soirée. La
 * journée D montre le petit-déjeuner du matin D (soirée D-1), puis le dîner et la nuit du soir D (soirée D). Le
 * premier jour de la saison n'a pas de petit-déjeuner ; le lendemain de la dernière soirée n'a que lui.
 */
public final class Day {

    /** Postes d'une journée dans l'ordre d'affichage. */
    public static final List<Post> POSTS = List.of(
            Post.BREAKFAST, Post.MAIN_DISH, Post.SIDES, Post.DINNER_PRESENCE, Post.NIGHT);

    /** Inscriptions proposées par « Je participe », dans l'ordre de la carte. */
    private static final List<Offered> OPTIONS = List.of(
            new Offered(RegistrationType.BREAKFAST, null),
            new Offered(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
            new Offered(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
            new Offered(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
            new Offered(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES),
            new Offered(RegistrationType.PICK_UP_AND_SHARE, null),
            new Offered(RegistrationType.NIGHT_AND_DINNER, null));

    private final LocalDate date;
    private final Evening previous;
    private final Evening current;

    /**
     * @param previous soirée D-1, qui porte le petit-déjeuner ; {@code null} le premier jour
     * @param current soirée D, qui porte le dîner et la nuit ; {@code null} le lendemain de la dernière soirée
     */
    public Day(LocalDate date, Evening previous, Evening current) {
        if (previous == null && current == null) {
            throw new IllegalArgumentException("A day needs at least one evening");
        }
        if (previous != null && !previous.date().equals(date.minusDays(1))) {
            throw new IllegalArgumentException("The previous evening must be the day before");
        }
        if (current != null && !current.date().equals(date)) {
            throw new IllegalArgumentException("The current evening must be the same day");
        }
        this.date = date;
        this.previous = previous;
        this.current = current;
    }

    /** Journées couvertes par des soirées, dans l'ordre des dates : chaque soirée apparaît sur deux journées. */
    public static List<Day> group(List<Evening> evenings) {
        Map<LocalDate, Evening> byDate = evenings.stream()
                .collect(Collectors.toMap(Evening::date, Function.identity(), (a, b) -> {
                    throw new IllegalArgumentException("Two evenings on " + a.date());
                }, TreeMap::new));
        var dates = new TreeSet<LocalDate>();
        for (var evening : byDate.keySet()) {
            dates.add(evening);
            dates.add(evening.plusDays(1));
        }
        return dates.stream()
                .map(day -> new Day(day, byDate.get(day.minusDays(1)), byDate.get(day)))
                .toList();
    }

    public LocalDate date() {
        return date;
    }

    /** Soirée qui porte ce poste ce jour-là, vide si le poste n'existe pas ce jour-là. */
    public Optional<Evening> eveningOf(Post post) {
        return Optional.ofNullable(post == Post.BREAKFAST ? previous : current);
    }

    /** Postes de la journée, dans l'ordre d'affichage. */
    public List<Post> posts() {
        return POSTS.stream().filter(post -> eveningOf(post).isPresent()).toList();
    }

    /** R5 : places manquantes sur un poste de la journée pour atteindre son minimum. */
    public int missing(Post post) {
        return eveningOf(post).map(evening -> evening.missing(post)).orElse(0);
    }

    /** R5 : complète si chaque poste de la journée couvre son minimum, sinon à compléter. */
    public DayStatus status() {
        return posts().stream().allMatch(post -> missing(post) == 0) ? DayStatus.COMPLETE : DayStatus.INCOMPLETE;
    }

    /**
     * Inscriptions actives du bénévole montrées sur cette journée (petit-déjeuner, puis dîner ou nuit) : deux au plus,
     * une par soirée (R2).
     */
    public List<DayRegistration> registrationsOf(UUID volunteerId) {
        var mine = new ArrayList<DayRegistration>();
        for (var evening : new Evening[] {previous, current}) {
            if (evening == null) {
                continue;
            }
            for (var registration : evening.registrations()) {
                if (volunteerId.equals(registration.volunteerId())
                        && registration.type().activityDate(evening.date()).equals(date)) {
                    mine.add(new DayRegistration(evening.date(), registration));
                }
            }
        }
        return mine;
    }

    /** Le bénévole a une inscription active montrée sur cette journée (filtre « Les miennes »). */
    public boolean involves(UUID volunteerId) {
        return !registrationsOf(volunteerId).isEmpty();
    }

    /** La journée est passée à partir de minuit le lendemain, heure de Paris. */
    public boolean isPast(LocalDate today) {
        return date.isBefore(today);
    }

    /**
     * « Je participe » : inscriptions encore possibles pour ce bénévole, chacune décidée sur sa soirée par
     * {@link Evening#decide} (R1 à R4 et R7 par soirée, sans changement). Le petit-déjeuner du jour même n'est plus
     * proposé : sa soirée est passée.
     */
    public DayOffer offers(Season season, UUID volunteerId, Instant now) {
        var available = new ArrayList<Offer>();
        RegistrationDecision blockingCurrent = null;
        RegistrationDecision blockingPrevious = null;
        for (var option : OPTIONS) {
            var evening = option.type() == RegistrationType.BREAKFAST ? previous : current;
            if (evening == null) {
                continue;
            }
            var decision = evening.decide(season,
                    Registration.by(volunteerId, option.type(), option.dishChoice()), now);
            if (decision.accepted()) {
                available.add(new Offer(evening.date(), option.type(), option.dishChoice()));
            } else if (decision != RegistrationDecision.FULL && decision != RegistrationDecision.DISH_TAKEN) {
                if (evening == current) {
                    blockingCurrent = decision;
                } else {
                    blockingPrevious = decision;
                }
            }
        }
        if (!available.isEmpty()) {
            return new DayOffer(available, null);
        }
        // Le motif du soir prime : c'est l'essentiel de la journée.
        var blocking = blockingCurrent != null ? blockingCurrent
                : blockingPrevious != null ? blockingPrevious : RegistrationDecision.FULL;
        return new DayOffer(List.of(), blocking);
    }

    private record Offered(RegistrationType type, DishChoice dishChoice) {
    }
}
