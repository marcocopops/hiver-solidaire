package com.marcantoinemeunier.hiversolidaire.planning;

import com.marcantoinemeunier.hiversolidaire.planning.PublicPlanning.MyRegistration;
import com.marcantoinemeunier.hiversolidaire.planning.PublicPlanning.PostLine;
import com.marcantoinemeunier.hiversolidaire.planning.PublicPlanning.PublicDay;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Cancellation;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DayRegistration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.DayStatus;
import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Registration;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Season;
import com.marcantoinemeunier.hiversolidaire.planning.persistence.EveningEntity;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
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
     * Journées à venir de la saison courante, aujourd'hui compris ; une journée disparaît à minuit le lendemain,
     * heure de Paris. Les soirées sont lues à partir de la veille : le petit-déjeuner du jour appartient à la soirée
     * d'hier.
     */
    @Transactional(readOnly = true)
    PublicPlanning load(Optional<UUID> volunteerId, PlanningFilter filter) {
        var now = clock.instant();
        var today = ParisTime.today(now);
        return seasons.findCurrent(today).map(season -> load(season, today, now, volunteerId, filter))
                .orElseGet(() -> PublicPlanning.none(filter, volunteerId.isPresent()));
    }

    private PublicPlanning load(SeasonEntity season, LocalDate today, Instant now, Optional<UUID> volunteerId,
            PlanningFilter filter) {
        var from = today.minusDays(1);
        var needs = season.needs();
        var domainSeason = season.toDomain();
        Map<UUID, List<Registration>> byEvening = registrations.findFromDate(season.id(), from).stream()
                .collect(Collectors.groupingBy(RegistrationEntity::eveningId,
                        Collectors.mapping(RegistrationEntity::toDomain, Collectors.toList())));
        Map<UUID, List<RegistrantPseudo>> pseudos = registrations.findPseudosFromDate(season.id(), from).stream()
                .collect(Collectors.groupingBy(RegistrantPseudo::eveningId));
        var entities = evenings.findFromDate(season.id(), from);
        Map<LocalDate, UUID> eveningIds = entities.stream()
                .collect(Collectors.toMap(EveningEntity::date, EveningEntity::id));
        var domainEvenings = entities.stream()
                .map(evening -> evening.toDomain(needs, byEvening.getOrDefault(evening.id(), List.of())))
                .toList();
        Function<LocalDate, List<RegistrantPseudo>> pseudosOf =
                date -> pseudos.getOrDefault(eveningIds.get(date), List.of());
        var upcoming = Day.group(domainEvenings).stream().filter(day -> !day.isPast(today)).toList();
        var days = upcoming.stream()
                .filter(day -> switch (filter) {
                    case ALL -> true;
                    case TO_COMPLETE -> day.status() == DayStatus.INCOMPLETE;
                    case MINE -> volunteerId.map(day::involves).orElse(false);
                })
                .map(day -> toView(day, pseudosOf, domainSeason, now, volunteerId))
                .toList();
        var opensOn = now.isBefore(domainSeason.registrationsOpenAt())
                ? OPENING.format(domainSeason.registrationsOpenAt().atZone(ParisTime.ZONE))
                : null;
        return new PublicPlanning(season.label(), opensOn, filter, volunteerId.isPresent(), !upcoming.isEmpty(),
                days);
    }

    private static PublicDay toView(Day day, Function<LocalDate, List<RegistrantPseudo>> pseudosOf, Season season,
            Instant now, Optional<UUID> volunteerId) {
        var status = day.status();
        var posts = day.posts().stream()
                .map(post -> postLine(day, post, pseudosOf.apply(day.eveningOf(post).orElseThrow().date()),
                        volunteerId))
                .toList();
        // Un visiteur fictif suffit pour R7 et R1 ; le bénévole connecté vérifie aussi R2, soirée par soirée.
        var who = volunteerId.orElseGet(UUID::randomUUID);
        var registrable = !day.offers(season, who, now).available().isEmpty();
        var mine = volunteerId.map(id -> myRegistrations(day.registrationsOf(id), now)).orElse(List.of());
        return new PublicDay(day.date().toString(), DayLabelFormatter.format(day.date()),
                status.name().toLowerCase(Locale.ROOT), status == DayStatus.INCOMPLETE, registrable, posts, mine);
    }

    /** Pseudos d'un poste : ceux des inscrits de la soirée qui porte ce poste, le sien remplacé par « Vous ». */
    private static PostLine postLine(Day day, Post post, List<RegistrantPseudo> pseudos, Optional<UUID> volunteerId) {
        var covering = pseudos.stream().filter(registrant -> registrant.coveredPosts().contains(post)).toList();
        var you = volunteerId.map(id -> covering.stream().anyMatch(registrant -> id.equals(registrant.volunteerId())))
                .orElse(false);
        var others = covering.stream()
                .filter(registrant -> volunteerId.map(id -> !id.equals(registrant.volunteerId())).orElse(true))
                .map(RegistrantPseudo::pseudo)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new PostLine(post.name(), "planning.post." + post.name(),
                post == Post.NIGHT ? "planning.post.NIGHT.hint" : null, you, others, day.missing(post));
    }

    /** Une ligne par inscription ; le bouton ne précise le repas que si la carte en porte deux. */
    private static List<MyRegistration> myRegistrations(List<DayRegistration> mine, Instant now) {
        return mine.stream().map(entry -> {
            var registration = entry.registration();
            var cancelKey = mine.size() == 1 ? "planning.cancel" : "planning.cancel." + meal(registration.type());
            return new MyRegistration(entry.eveningDate().toString(),
                    "planning.you." + choiceParam(registration),
                    cancelKey,
                    Cancellation.allowed(entry.eveningDate(), now),
                    DayLabelFormatter.deadline(Cancellation.deadline(entry.eveningDate())));
        }).toList();
    }

    private static String meal(RegistrationType type) {
        return switch (type) {
            case BREAKFAST, NIGHT_AND_DINNER -> type.name();
            case PREPARE_AND_SHARE, PREPARE_AT_HOME, PICK_UP_AND_SHARE -> "DINNER";
        };
    }

    private static String choiceParam(Registration registration) {
        return registration.dishChoice() == null ? registration.type().name()
                : registration.type().name() + "." + registration.dishChoice().name();
    }
}
