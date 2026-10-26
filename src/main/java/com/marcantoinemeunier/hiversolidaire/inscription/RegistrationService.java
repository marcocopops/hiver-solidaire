package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.notification.CoordinatorAlerts;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Cancellation;
import com.marcantoinemeunier.hiversolidaire.planning.domain.CancellationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.IncompleteEveningAlert;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.UpcomingRegistration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

@Service
public class RegistrationService {

    private final SeasonRepository seasons;
    private final EveningRepository evenings;
    private final RegistrationRepository registrations;
    private final CoordinatorAlerts alerts;
    private final Clock clock;

    RegistrationService(SeasonRepository seasons, EveningRepository evenings, RegistrationRepository registrations,
            CoordinatorAlerts alerts, Clock clock) {
        this.seasons = seasons;
        this.evenings = evenings;
        this.registrations = registrations;
        this.alerts = alerts;
        this.clock = clock;
    }

    /**
     * Ce qu'il faut pour afficher le choix d'une journée : les options encore disponibles, chacune sur sa soirée
     * (R1, R3, et R2 pour un bénévole connecté), ou le motif qui bloque tout (R7, R2, R1).
     */
    record DayChoice(LocalDate day, RegistrationDecision blocking, List<String> available) {
    }

    /**
     * R1 à R7 à l'écriture : verrou exclusif sur la soirée ({@code SELECT … FOR UPDATE}), lecture des inscriptions
     * existantes, décision du domaine, puis insertion, dans la même transaction. Deux inscriptions sur la même
     * soirée s'exécutent l'une après l'autre.
     */
    @Transactional
    public RegistrationDecision register(UUID volunteerId, RegistrationChoice choice) {
        var season = currentSeason().orElseThrow(UnknownEveningException::new);
        var eveningId = evenings.findIdBySeasonAndDate(season.id(), choice.eveningDate())
                .orElseThrow(UnknownEveningException::new);
        var evening = evenings.lockById(eveningId).orElseThrow(UnknownEveningException::new);
        var candidate = Registration.by(volunteerId, choice.type(), choice.dishChoice());
        var decision = toDomain(season, evening).decide(season.toDomain(), candidate, clock.instant());
        if (decision.accepted()) {
            try {
                registrations.saveAndFlush(new RegistrationEntity(evening.id(), candidate, clock.instant()));
            } catch (DataIntegrityViolationException e) {
                // Filet de sécurité (contraintes R2, R3) : ne devrait pas arriver sous le verrou.
                throw new RegistrationConflictException(e);
            }
        }
        return decision;
    }

    /**
     * Désinscription (R6) sous le même verrou que l'inscription (R1) : une annulation et une inscription simultanées
     * sur la même soirée s'exécutent l'une après l'autre. Le bénévole est celui de la session : il n'annule que sa
     * propre inscription.
     */
    @Transactional
    public CancellationDecision cancel(UUID volunteerId, LocalDate eveningDate) {
        var season = currentSeason().orElseThrow(UnknownEveningException::new);
        var eveningId = evenings.findIdBySeasonAndDate(season.id(), eveningDate)
                .orElseThrow(UnknownEveningException::new);
        return cancelUnderLock(volunteerId, eveningId, true);
    }

    /**
     * Suppression du compte : annule toutes les inscriptions actives dont la soirée n'est pas passée, y compris
     * après la limite de R6. Les soirées sont verrouillées dans l'ordre des dates : pas d'interblocage avec une
     * inscription concurrente, qui n'en verrouille qu'une.
     */
    @Transactional
    public void cancelAllUpcoming(UUID volunteerId) {
        for (var eveningId : registrations.findActiveEveningIds(volunteerId, ParisTime.today(clock.instant()))) {
            cancelUnderLock(volunteerId, eveningId, false);
        }
    }

    private CancellationDecision cancelUnderLock(UUID volunteerId, UUID eveningId, boolean enforceDeadline) {
        var evening = evenings.lockById(eveningId).orElseThrow(UnknownEveningException::new);
        var season = seasons.findById(evening.seasonId()).orElseThrow(UnknownEveningException::new);
        var now = clock.instant();
        var before = toDomain(season, evening);
        var decision = before.decideCancellation(volunteerId, now);
        if (decision == CancellationDecision.NOT_REGISTERED
                || (decision == CancellationDecision.TOO_LATE && enforceDeadline)) {
            return decision;
        }
        registrations.findActive(eveningId, volunteerId).orElseThrow().cancel(now);
        if (IncompleteEveningAlert.triggeredBy(before, before.without(volunteerId), ParisTime.today(now))) {
            alerts.eveningIncomplete(eveningId);
        }
        return CancellationDecision.ACCEPTED;
    }

    /** R6 : le bénévole peut encore se désinscrire seul de cette soirée. */
    public boolean cancellable(LocalDate eveningDate) {
        return Cancellation.allowed(eveningDate, clock.instant());
    }

    /**
     * Choix d'une journée (ADR-0009) : le petit-déjeuner de la soirée de la veille, puis le dîner et la nuit de la
     * soirée du jour. Vide si aucune des deux soirées n'existe dans la saison courante.
     */
    @Transactional(readOnly = true)
    public Optional<DayChoice> choice(LocalDate day, Optional<UUID> volunteerId) {
        return currentSeason().flatMap(season -> {
            var previous = evenings.findBySeasonAndDate(season.id(), day.minusDays(1))
                    .map(evening -> toDomain(season, evening)).orElse(null);
            var current = evenings.findBySeasonAndDate(season.id(), day)
                    .map(evening -> toDomain(season, evening)).orElse(null);
            if (previous == null && current == null) {
                return Optional.empty();
            }
            // Un bénévole fictif suffit pour vérifier R7 ; le vrai bénévole connecté vérifie aussi R2.
            var who = volunteerId.orElseGet(UUID::randomUUID);
            var offers = new Day(day, previous, current).offers(season.toDomain(), who, clock.instant());
            var available = offers.available().stream()
                    .map(offer -> RegistrationChoice.choiceParam(offer.type(), offer.dishChoice()))
                    .toList();
            return Optional.of(new DayChoice(day, offers.blocking(), available));
        });
    }

    /** Inscriptions à venir du bénévole, et seulement les siennes, dans l'ordre des jours d'activité. */
    @Transactional(readOnly = true)
    public List<UpcomingRegistration> upcoming(UUID volunteerId) {
        return registrations.findUpcoming(volunteerId, ParisTime.today(clock.instant())).stream()
                .sorted(Comparator.comparing(registration -> registration.type().activityDate(registration.date())))
                .toList();
    }

    /** Inscription active à venir du bénévole sur cette soirée, et seulement la sienne. */
    @Transactional(readOnly = true)
    public Optional<UpcomingRegistration> upcoming(UUID volunteerId, LocalDate eveningDate) {
        return upcoming(volunteerId).stream().filter(registration -> registration.date().equals(eveningDate))
                .findFirst();
    }

    private Optional<SeasonEntity> currentSeason() {
        return seasons.findCurrent(ParisTime.today(clock.instant()));
    }

    private Evening toDomain(SeasonEntity season, EveningEntity evening) {
        var existing = registrations.findActiveByEvening(evening.id()).stream().map(RegistrationEntity::toDomain).toList();
        return evening.toDomain(season.needs(), existing);
    }

    /** La soirée demandée n'existe pas dans la saison courante. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class UnknownEveningException extends RuntimeException {
    }

    /** Une contrainte de la base a refusé l'inscription (course improbable sous le verrou). */
    @ResponseStatus(HttpStatus.CONFLICT)
    static class RegistrationConflictException extends RuntimeException {

        RegistrationConflictException(Throwable cause) {
            super(cause);
        }
    }
}
