package com.marcantoinemeunier.hiversolidaire.admin;

import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DayStatus;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationContact;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectures du back-office sur le planning de la saison courante : journées (ADR-0009), soirées et inscrits avec leurs
 * coordonnées. Lecture seule ; les écritures passent par {@code RegistrationService}, sous le verrou de R1.
 */
@Service
class CoordinatorPlanning {

    private final SeasonRepository seasons;
    private final EveningRepository evenings;
    private final RegistrationRepository registrations;
    private final Clock clock;

    CoordinatorPlanning(SeasonRepository seasons, EveningRepository evenings, RegistrationRepository registrations,
            Clock clock) {
        this.seasons = seasons;
        this.evenings = evenings;
        this.registrations = registrations;
        this.clock = clock;
    }

    /**
     * Journées de {@code from} à {@code to} (bornes comprises) et leurs inscrits.
     *
     * @param days journées de la période, dans l'ordre des dates (celles que couvre la saison)
     * @param contacts inscrits actifs des soirées de la période, par soirée (soir J)
     * @param eveningIds identifiant de chaque soirée, par date
     */
    record Window(SeasonEntity season, List<Day> days, Map<LocalDate, List<RegistrationContact>> contacts,
            Map<LocalDate, UUID> eveningIds) {

        Optional<Day> day(LocalDate date) {
            return days.stream().filter(day -> day.date().equals(date)).findFirst();
        }

        List<RegistrationContact> contactsOn(LocalDate eveningDate, Post post) {
            return contacts.getOrDefault(eveningDate, List.of()).stream()
                    .filter(contact -> contact.type().coveredPosts(contact.dishChoice()).contains(post))
                    .toList();
        }
    }

    /** Inscription active et coordonnées de son bénévole. */
    @Transactional(readOnly = true)
    Optional<RegistrationContact> registration(UUID registrationId) {
        return registrations.findActiveContact(registrationId);
    }

    /** Inscriptions actives d'un bénévole, passées comprises. */
    @Transactional(readOnly = true)
    List<RegistrationContact> registrationsOf(UUID volunteerId) {
        return registrations.findContactsOfVolunteer(volunteerId);
    }

    LocalDate today() {
        return ParisTime.today(clock.instant());
    }

    Optional<SeasonEntity> currentSeason() {
        return seasons.findCurrent(today());
    }

    /** La soirée D-1 porte le petit-déjeuner du jour D : on lit à partir de la veille. */
    @Transactional(readOnly = true)
    Optional<Window> load(LocalDate from, LocalDate to) {
        return currentSeason().map(season -> load(season, from, to));
    }

    private Window load(SeasonEntity season, LocalDate from, LocalDate to) {
        var needs = season.needs();
        var entities = evenings.findFromDate(season.id(), from.minusDays(1)).stream()
                .filter(evening -> !evening.date().isAfter(to))
                .toList();
        Map<UUID, List<Registration>> byEvening = registrations.findFromDate(season.id(), from.minusDays(1)).stream()
                .collect(Collectors.groupingBy(RegistrationEntity::eveningId,
                        Collectors.mapping(RegistrationEntity::toDomain, Collectors.toList())));
        List<Evening> domain = entities.stream()
                .map(evening -> evening.toDomain(needs, byEvening.getOrDefault(evening.id(), List.of())))
                .toList();
        var days = Day.group(domain).stream()
                .filter(day -> !day.date().isBefore(from) && !day.date().isAfter(to))
                .toList();
        var contacts = registrations.findContacts(season.id(), from.minusDays(1), to).stream()
                .collect(Collectors.groupingBy(RegistrationContact::eveningDate));
        var ids = entities.stream().collect(Collectors.toMap(EveningEntity::date, EveningEntity::id));
        return new Window(season, days, contacts, ids);
    }

    /** Journées à compléter de la période (R5). */
    @Transactional(readOnly = true)
    List<Day> incompleteDays(LocalDate from, LocalDate to) {
        return load(from, to).map(window -> window.days().stream()
                .filter(day -> day.status() == DayStatus.INCOMPLETE).toList()).orElse(List.of());
    }

    /** R4 : soirées à venir où au moins une préparation chez soi n'a pas de récupération. */
    @Transactional(readOnly = true)
    List<Evening> eveningsNeedingPickup() {
        return currentSeason().map(season -> {
            var needs = season.needs();
            var today = today();
            Map<UUID, List<Registration>> byEvening = registrations.findFromDate(season.id(), today).stream()
                    .collect(Collectors.groupingBy(RegistrationEntity::eveningId,
                            Collectors.mapping(RegistrationEntity::toDomain, Collectors.toList())));
            return evenings.findFromDate(season.id(), today).stream()
                    .map(evening -> evening.toDomain(needs, byEvening.getOrDefault(evening.id(), List.of())))
                    .filter(Evening::needsPickupAlert)
                    .toList();
        }).orElse(List.of());
    }
}
