package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.notification.CoordinatorAlerts;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Cancellation;
import com.marcantoinemeunier.hiversolidaire.planning.domain.CancellationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.IncompleteEveningAlert;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.PickupWithoutMealAlert;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.UpcomingRegistration;
import java.time.Clock;
import java.time.Instant;
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
     * (R1, R3, R4, et R2 pour un bénévole connecté), ou le motif qui bloque tout (R7, ou R1 quand plus rien n'est
     * possible).
     */
    public record DayChoice(LocalDate day, RegistrationDecision blocking, List<String> available) {
    }

    /**
     * R1 à R7 à l'écriture : verrou exclusif sur la soirée ({@code SELECT … FOR UPDATE}), lecture des inscriptions
     * existantes, décision du domaine, puis insertion, dans la même transaction. Deux inscriptions sur la même
     * soirée s'exécutent l'une après l'autre.
     */
    @Transactional
    public RegistrationDecision register(UUID volunteerId, RegistrationChoice choice) {
        return registerFor(volunteerId, choice).decision();
    }

    /**
     * Résultat d'une inscription, avec ce qu'il faut pour le journal d'audit du back-office.
     *
     * @param registrationId identifiant de l'inscription créée, nul si elle est refusée
     */
    public record Registered(RegistrationDecision decision, UUID registrationId, UUID eveningId) {
    }

    /**
     * Inscription d'un bénévole, en ligne ou par un coordinateur (inscription par téléphone) : mêmes règles R1 à R4 et
     * R7, R2 pour ce bénévole, sous le même verrou.
     */
    @Transactional
    public Registered registerFor(UUID volunteerId, RegistrationChoice choice) {
        var season = currentSeason().orElseThrow(UnknownEveningException::new);
        var eveningId = evenings.findIdBySeasonAndDate(season.id(), choice.eveningDate())
                .orElseThrow(UnknownEveningException::new);
        var evening = evenings.lockById(eveningId).orElseThrow(UnknownEveningException::new);
        var candidate = Registration.by(volunteerId, choice.type(), choice.dishChoice());
        var decision = toDomain(season, evening).decide(season.toDomain(), candidate, clock.instant());
        if (!decision.accepted()) {
            return new Registered(decision, null, evening.id());
        }
        var created = insert(evening.id(), candidate);
        return new Registered(decision, created.id(), evening.id());
    }

    private RegistrationEntity insert(UUID eveningId, Registration candidate) {
        try {
            return registrations.saveAndFlush(new RegistrationEntity(eveningId, candidate, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité (contraintes R2, R3) : ne devrait pas arriver sous le verrou.
            throw new RegistrationConflictException(e);
        }
    }

    /** Issue d'une action d'un coordinateur sur une inscription existante. */
    public enum CoordinatorOutcome {
        DONE,
        /** L'inscription n'existe pas ou est déjà annulée. */
        NOT_FOUND,
        /** La soirée de l'inscription est passée : l'historique ne se modifie pas. */
        PAST,
        /** Déplacement vers la soirée où l'inscription est déjà. */
        SAME_EVENING,
        /** Déplacement refusé par les règles sur la soirée cible ({@code refusal}). */
        REFUSED
    }

    /**
     * Résultat d'une annulation ou d'un déplacement par un coordinateur, avec ce qu'il faut pour le journal d'audit.
     *
     * @param refusal motif du refus sur la soirée cible d'un déplacement (R1 à R4, R7), sinon nul
     * @param newRegistrationId inscription créée sur la soirée cible d'un déplacement
     */
    public record CoordinatorChange(CoordinatorOutcome outcome, RegistrationDecision refusal, UUID volunteerId,
            UUID eveningId, UUID targetEveningId, UUID newRegistrationId) {

        static CoordinatorChange of(CoordinatorOutcome outcome) {
            return new CoordinatorChange(outcome, null, null, null, null, null);
        }
    }

    /**
     * Annulation par un coordinateur (§6 « Fiche soirée »), même après la limite de R6 : le bénévole a prévenu par
     * téléphone. Même verrou que l'inscription, même alerte « soirée devenue incomplète ». Jamais sur une soirée passée.
     */
    @Transactional
    public CoordinatorChange cancelByCoordinator(UUID registrationId) {
        var eveningId = registrations.findActiveEveningId(registrationId);
        if (eveningId.isEmpty()) {
            return CoordinatorChange.of(CoordinatorOutcome.NOT_FOUND);
        }
        var evening = evenings.lockById(eveningId.get()).orElseThrow(UnknownEveningException::new);
        var registration = registrations.findActiveById(registrationId).filter(r -> r.eveningId().equals(evening.id()));
        if (registration.isEmpty()) {
            return CoordinatorChange.of(CoordinatorOutcome.NOT_FOUND);
        }
        var now = clock.instant();
        if (evening.date().isBefore(ParisTime.today(now))) {
            return CoordinatorChange.of(CoordinatorOutcome.PAST);
        }
        var volunteerId = registration.get().volunteerId();
        cancelLocked(evening, registration.get(), now);
        return new CoordinatorChange(CoordinatorOutcome.DONE, null, volunteerId, evening.id(), null, null);
    }

    /**
     * Déplacement par un coordinateur vers une autre soirée de la saison courante, avec le même type et le même plat :
     * la soirée cible passe par les mêmes contrôles qu'une inscription (R1 à R4 et R7, R2 pour ce bénévole). Les deux
     * soirées sont verrouillées dans l'ordre des dates (pas d'interblocage avec un autre déplacement). L'inscription
     * d'origine est annulée et une nouvelle est créée : l'historique reste, et les alertes « soirée devenue incomplète »
     * et « récupération sans repas » valent pour la soirée quittée.
     */
    @Transactional
    public CoordinatorChange moveByCoordinator(UUID registrationId, LocalDate targetEveningDate) {
        var season = currentSeason().orElseThrow(UnknownEveningException::new);
        var sourceId = registrations.findActiveEveningId(registrationId);
        var targetId = evenings.findIdBySeasonAndDate(season.id(), targetEveningDate);
        if (sourceId.isEmpty()) {
            return CoordinatorChange.of(CoordinatorOutcome.NOT_FOUND);
        }
        if (targetId.isEmpty()) {
            throw new UnknownEveningException();
        }
        if (sourceId.get().equals(targetId.get())) {
            return CoordinatorChange.of(CoordinatorOutcome.SAME_EVENING);
        }
        var source = evenings.findById(sourceId.get()).orElseThrow(UnknownEveningException::new);
        var targetFirst = targetEveningDate.isBefore(source.date());
        var first = evenings.lockById(targetFirst ? targetId.get() : sourceId.get()).orElseThrow();
        var second = evenings.lockById(targetFirst ? sourceId.get() : targetId.get()).orElseThrow();
        var lockedSource = targetFirst ? second : first;
        var lockedTarget = targetFirst ? first : second;
        var registration = registrations.findActiveById(registrationId)
                .filter(r -> r.eveningId().equals(lockedSource.id()));
        if (registration.isEmpty()) {
            return CoordinatorChange.of(CoordinatorOutcome.NOT_FOUND);
        }
        var now = clock.instant();
        if (lockedSource.date().isBefore(ParisTime.today(now))) {
            return CoordinatorChange.of(CoordinatorOutcome.PAST);
        }
        var volunteerId = registration.get().volunteerId();
        var moved = registration.get().toDomain();
        var candidate = Registration.by(volunteerId, moved.type(), moved.dishChoice());
        var decision = toDomain(season, lockedTarget).decide(season.toDomain(), candidate, now);
        if (!decision.accepted()) {
            return new CoordinatorChange(CoordinatorOutcome.REFUSED, decision, volunteerId, lockedSource.id(),
                    lockedTarget.id(), null);
        }
        cancelLocked(lockedSource, registration.get(), now);
        var created = insert(lockedTarget.id(), candidate);
        return new CoordinatorChange(CoordinatorOutcome.DONE, null, volunteerId, lockedSource.id(), lockedTarget.id(),
                created.id());
    }

    /**
     * Annule une inscription dont la soirée est déjà verrouillée, avec les alertes éventuelles. Seule cette inscription
     * est retirée du calcul : les autres postes du même bénévole restent (R2, ADR-0011).
     */
    private void cancelLocked(EveningEntity evening, RegistrationEntity registration, Instant now) {
        var season = seasons.findById(evening.seasonId()).orElseThrow(UnknownEveningException::new);
        var before = toDomain(season, evening);
        registration.cancel(now);
        registrations.saveAndFlush(registration);
        recordAlerts(evening.id(), before, before.without(registration.toDomain()), now);
    }

    /**
     * Alertes aux coordinateurs après une annulation, dans la même transaction : soirée des 7 prochains jours devenue
     * incomplète (§6), récupération restée sans repas (R4).
     */
    private void recordAlerts(UUID eveningId, Evening before, Evening after, Instant now) {
        if (IncompleteEveningAlert.triggeredBy(before, after, ParisTime.today(now))) {
            alerts.eveningIncomplete(eveningId);
        }
        if (PickupWithoutMealAlert.triggeredBy(before, after)) {
            alerts.pickupWithoutMeal(eveningId);
        }
    }

    /**
     * Désinscription (R6) d'un poste, sous le même verrou que l'inscription (R1) : une annulation et une inscription
     * simultanées sur la même soirée s'exécutent l'une après l'autre. Le bénévole est celui de la session : il n'annule
     * que sa propre inscription, désignée par sa soirée et son choix (type et plat), unique pour lui grâce à R2. Une
     * préparation chez soi déjà récupérée s'annule aussi : la récupération reste et les coordinateurs sont alertés.
     */
    @Transactional
    public CancellationDecision cancel(UUID volunteerId, LocalDate eveningDate, RegistrationType type,
            DishChoice dishChoice) {
        var season = currentSeason().orElseThrow(UnknownEveningException::new);
        var eveningId = evenings.findIdBySeasonAndDate(season.id(), eveningDate)
                .orElseThrow(UnknownEveningException::new);
        var evening = evenings.lockById(eveningId).orElseThrow(UnknownEveningException::new);
        var now = clock.instant();
        var before = toDomain(season, evening);
        var target = Registration.by(volunteerId, type, dishChoice);
        var decision = before.decideCancellation(target, now);
        if (!decision.accepted()) {
            return decision;
        }
        registrations.findActiveOf(eveningId, volunteerId).stream()
                .filter(registration -> registration.toDomain().equals(target))
                .findFirst().orElseThrow()
                .cancel(now);
        recordAlerts(eveningId, before, before.without(target), now);
        return CancellationDecision.ACCEPTED;
    }

    /**
     * Suppression du compte : annule toutes les inscriptions actives dont la soirée n'est pas passée, tous postes
     * confondus, y compris après la limite de R6. Les soirées sont verrouillées dans l'ordre des dates : pas
     * d'interblocage avec une inscription concurrente, qui n'en verrouille qu'une. Une alerte par cause et par soirée,
     * pas une par inscription.
     */
    @Transactional
    public void cancelAllUpcoming(UUID volunteerId) {
        var now = clock.instant();
        for (var eveningId : registrations.findActiveEveningIds(volunteerId, ParisTime.today(now))) {
            var evening = evenings.lockById(eveningId).orElseThrow(UnknownEveningException::new);
            var season = seasons.findById(evening.seasonId()).orElseThrow(UnknownEveningException::new);
            var before = toDomain(season, evening);
            registrations.findActiveOf(eveningId, volunteerId).forEach(registration -> registration.cancel(now));
            recordAlerts(eveningId, before, before.without(volunteerId), now);
        }
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

    /**
     * Inscriptions à venir du bénévole, et seulement les siennes, dans l'ordre des jours d'activité, puis des postes de
     * la journée ({@link Day#POSTS}).
     */
    @Transactional(readOnly = true)
    public List<UpcomingRegistration> upcoming(UUID volunteerId) {
        return registrations.findUpcoming(volunteerId, ParisTime.today(clock.instant())).stream()
                .sorted(Comparator.comparing((UpcomingRegistration registration) ->
                                registration.type().activityDate(registration.date()))
                        .thenComparingInt(registration -> Day.POSTS.indexOf(
                                Registration.of(registration.type(), registration.dishChoice()).ownPost())))
                .toList();
    }

    /** Inscription active à venir du bénévole sur cette soirée, pour ce choix, et seulement la sienne. */
    @Transactional(readOnly = true)
    public Optional<UpcomingRegistration> upcoming(UUID volunteerId, LocalDate eveningDate, RegistrationType type,
            DishChoice dishChoice) {
        return upcoming(volunteerId).stream()
                .filter(registration -> registration.date().equals(eveningDate) && registration.type() == type
                        && registration.dishChoice() == dishChoice)
                .findFirst();
    }

    /**
     * R4 : désinscrire cette inscription laisserait une récupération sans repas (avertissement de la page de
     * confirmation). Lecture sans verrou, à titre indicatif : l'alerte est décidée sous le verrou, à l'annulation.
     */
    @Transactional(readOnly = true)
    public boolean leavesPickupWithoutMeal(UUID volunteerId, LocalDate eveningDate, RegistrationType type,
            DishChoice dishChoice) {
        var target = Registration.by(volunteerId, type, dishChoice);
        return currentSeason()
                .flatMap(season -> evenings.findBySeasonAndDate(season.id(), eveningDate)
                        .map(evening -> toDomain(season, evening)))
                .filter(evening -> evening.registrations().contains(target))
                .map(evening -> PickupWithoutMealAlert.triggeredBy(evening, evening.without(target)))
                .orElse(false);
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
