package com.marcantoinemeunier.hiversolidaire.planning;

import com.marcantoinemeunier.hiversolidaire.planning.PublicPlanning.PublicEvening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.EveningStatus;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublicPlanningService {

    private final SeasonRepository seasons;
    private final EveningRepository evenings;
    private final RegistrationRepository registrations;
    private final Clock clock;

    PublicPlanningService(SeasonRepository seasons, EveningRepository evenings,
            RegistrationRepository registrations, Clock clock) {
        this.seasons = seasons;
        this.evenings = evenings;
        this.registrations = registrations;
        this.clock = clock;
    }

    /** Soirées à venir de la saison courante ; les soirées passées (R7) sont masquées. */
    @Transactional(readOnly = true)
    PublicPlanning load() {
        var today = ParisTime.today(clock.instant());
        return seasons.findCurrent(today).map(season -> load(season, today)).orElseGet(PublicPlanning::none);
    }

    private PublicPlanning load(SeasonEntity season, LocalDate today) {
        var needs = season.needs();
        Map<UUID, List<Registration>> byEvening = registrations.findFromDate(season.id(), today).stream()
                .collect(Collectors.groupingBy(RegistrationEntity::eveningId,
                        Collectors.mapping(RegistrationEntity::toDomain, Collectors.toList())));
        var upcoming = evenings.findFromDate(season.id(), today).stream()
                .map(evening -> evening.toDomain(needs, byEvening.getOrDefault(evening.id(), List.of())))
                .map(PublicPlanningService::toView)
                .toList();
        return new PublicPlanning(season.label(), season.sample(), upcoming);
    }

    private static PublicEvening toView(Evening evening) {
        var label = EveningLabelFormatter.format(evening.date());
        if (evening.closed()) {
            return new PublicEvening(label, "closed", List.of());
        }
        var status = evening.status();
        // Soirée vide : tout manque, le statut « Personne pour l'instant » suffit.
        var missing = status == EveningStatus.EMPTY ? List.<String>of()
                : evening.missingPosts().stream().map(post -> "planning.post." + post.name()).toList();
        return new PublicEvening(label, status.name().toLowerCase(Locale.ROOT), missing);
    }
}
