package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationDecision;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Season;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.UpcomingRegistration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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

    /** Types et plats dans l'ordre d'affichage du choix. */
    static final List<RegistrationChoiceOption> OPTIONS = List.of(
            new RegistrationChoiceOption(RegistrationType.PREPARE_AND_SHARE, DishChoice.MAIN_DISH),
            new RegistrationChoiceOption(RegistrationType.PREPARE_AND_SHARE, DishChoice.SIDES),
            new RegistrationChoiceOption(RegistrationType.PREPARE_AT_HOME, DishChoice.MAIN_DISH),
            new RegistrationChoiceOption(RegistrationType.PREPARE_AT_HOME, DishChoice.SIDES),
            new RegistrationChoiceOption(RegistrationType.PICK_UP_AND_SHARE, null),
            new RegistrationChoiceOption(RegistrationType.NIGHT_AND_DINNER, null),
            new RegistrationChoiceOption(RegistrationType.BREAKFAST, null));

    private final SeasonRepository seasons;
    private final EveningRepository evenings;
    private final RegistrationRepository registrations;
    private final Clock clock;

    RegistrationService(SeasonRepository seasons, EveningRepository evenings, RegistrationRepository registrations,
            Clock clock) {
        this.seasons = seasons;
        this.evenings = evenings;
        this.registrations = registrations;
        this.clock = clock;
    }

    /** Une option de choix : un type et, s'il prépare, son plat. */
    record RegistrationChoiceOption(RegistrationType type, DishChoice dishChoice) {

        String param() {
            return RegistrationChoice.choiceParam(type, dishChoice);
        }
    }

    /**
     * Ce qu'il faut pour afficher le choix d'une soirée : sa date, si elle accepte encore des inscriptions (R7,
     * et R2 pour un bénévole connecté) et les options encore disponibles (R1, R3).
     */
    record EveningChoice(LocalDate date, RegistrationDecision blocking, List<RegistrationChoiceOption> available) {
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

    @Transactional(readOnly = true)
    public Optional<EveningChoice> choice(LocalDate date, Optional<UUID> volunteerId) {
        return currentSeason().flatMap(season -> evenings.findBySeasonAndDate(season.id(), date)
                .map(evening -> choice(season, evening, volunteerId)));
    }

    private EveningChoice choice(SeasonEntity season, EveningEntity entity, Optional<UUID> volunteerId) {
        var evening = toDomain(season, entity);
        // Un bénévole fictif suffit pour vérifier R7 ; le vrai bénévole connecté vérifie aussi R2.
        var who = volunteerId.orElseGet(UUID::randomUUID);
        var available = new ArrayList<RegistrationChoiceOption>();
        RegistrationDecision blocking = null;
        for (var option : OPTIONS) {
            var decision = evening.decide(season.toDomain(),
                    Registration.by(who, option.type(), option.dishChoice()), clock.instant());
            if (decision.accepted()) {
                available.add(option);
            } else if (decision != RegistrationDecision.FULL && decision != RegistrationDecision.DISH_TAKEN) {
                blocking = decision;
            }
        }
        if (blocking == null && available.isEmpty()) {
            blocking = RegistrationDecision.FULL;
        }
        return new EveningChoice(entity.date(), blocking, blocking == null ? available : List.of());
    }

    /** Inscriptions à venir du bénévole, et seulement les siennes. */
    @Transactional(readOnly = true)
    public List<UpcomingRegistration> upcoming(UUID volunteerId) {
        return registrations.findUpcoming(volunteerId, ParisTime.today(clock.instant()));
    }

    private Optional<SeasonEntity> currentSeason() {
        return seasons.findCurrent(ParisTime.today(clock.instant()));
    }

    private Evening toDomain(SeasonEntity season, EveningEntity evening) {
        var existing = registrations.findByEvening(evening.id()).stream().map(RegistrationEntity::toDomain).toList();
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
