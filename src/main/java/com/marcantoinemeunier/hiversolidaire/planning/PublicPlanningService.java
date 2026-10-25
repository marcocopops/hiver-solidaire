package com.marcantoinemeunier.hiversolidaire.planning;

import com.marcantoinemeunier.hiversolidaire.planning.PublicPlanning.PostRegistrants;
import com.marcantoinemeunier.hiversolidaire.planning.PublicPlanning.PublicEvening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Evening;
import com.marcantoinemeunier.hiversolidaire.planning.domain.EveningStatus;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Season;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrantPseudo;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.RegistrationRepository;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonEntity;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.SeasonRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublicPlanningService {

    private static final DateTimeFormatter OPENING = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

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

    /**
     * Soirées à venir de la saison courante ; les soirées passées (R7) sont masquées. Pour un bénévole connecté,
     * ses propres soirées sont signalées.
     */
    @Transactional(readOnly = true)
    PublicPlanning load(Optional<UUID> volunteerId) {
        var now = clock.instant();
        var today = ParisTime.today(now);
        return seasons.findCurrent(today).map(season -> load(season, today, now, volunteerId))
                .orElseGet(PublicPlanning::none);
    }

    private PublicPlanning load(SeasonEntity season, LocalDate today, Instant now, Optional<UUID> volunteerId) {
        var needs = season.needs();
        var domainSeason = season.toDomain();
        Map<UUID, List<Registration>> byEvening = registrations.findFromDate(season.id(), today).stream()
                .collect(Collectors.groupingBy(RegistrationEntity::eveningId,
                        Collectors.mapping(RegistrationEntity::toDomain, Collectors.toList())));
        Map<UUID, List<RegistrantPseudo>> pseudos = registrations.findPseudosFromDate(season.id(), today).stream()
                .collect(Collectors.groupingBy(RegistrantPseudo::eveningId));
        var upcoming = evenings.findFromDate(season.id(), today).stream()
                .map(evening -> toView(evening.toDomain(needs, byEvening.getOrDefault(evening.id(), List.of())),
                        pseudos.getOrDefault(evening.id(), List.of()), domainSeason, now, volunteerId))
                .toList();
        var opensOn = now.isBefore(domainSeason.registrationsOpenAt())
                ? OPENING.format(domainSeason.registrationsOpenAt().atZone(ParisTime.ZONE))
                : null;
        return new PublicPlanning(season.label(), season.sample(), opensOn, upcoming);
    }

    private static PublicEvening toView(Evening evening, List<RegistrantPseudo> pseudos, Season season, Instant now,
            Optional<UUID> volunteerId) {
        var date = evening.date().toString();
        var label = EveningLabelFormatter.format(evening.date());
        var mine = volunteerId.map(id -> evening.registrations().stream()
                .anyMatch(registration -> id.equals(registration.volunteerId()))).orElse(false);
        if (evening.closed()) {
            // Soirée fermée : le statut seul, aucun pseudo.
            return new PublicEvening(date, label, "closed", List.of(), false, mine, List.of());
        }
        var status = evening.status();
        // Soirée vide : tout manque, le statut « Personne pour l'instant » suffit.
        var missing = status == EveningStatus.EMPTY ? List.<String>of()
                : evening.missingPosts().stream().map(post -> "planning.post." + post.name()).toList();
        var registrable = !mine && season.acceptsRegistrations(evening, now) && anyTypeAvailable(evening);
        return new PublicEvening(date, label, status.name().toLowerCase(Locale.ROOT), missing, registrable, mine,
                registrantsByPost(pseudos));
    }

    /** Pseudos par poste, dans l'ordre d'affichage des postes ; un pseudo peut figurer sous deux postes. */
    private static List<PostRegistrants> registrantsByPost(List<RegistrantPseudo> pseudos) {
        var byPost = new EnumMap<Post, List<String>>(Post.class);
        for (var registrant : pseudos) {
            for (var post : registrant.coveredPosts()) {
                byPost.computeIfAbsent(post, p -> new ArrayList<>()).add(registrant.pseudo());
            }
        }
        return byPost.entrySet().stream()
                .map(entry -> new PostRegistrants("planning.registrants." + entry.getKey().name(),
                        entry.getValue().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()))
                .toList();
    }

    private static boolean anyTypeAvailable(Evening evening) {
        for (RegistrationType type : RegistrationType.values()) {
            if (type.preparesDish()) {
                for (DishChoice dish : DishChoice.values()) {
                    if (evening.isAvailable(type, dish)) {
                        return true;
                    }
                }
            } else if (evening.isAvailable(type, null)) {
                return true;
            }
        }
        return false;
    }
}
