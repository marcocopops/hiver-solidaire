package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Choix fait sur le planning (journée, type, plat), porté de page en page par deux paramètres : {@code jour} (date de
 * la carte, unique dans la saison : aucun identifiant technique sur les pages publiques) et {@code choix}
 * ({@code TYPE} ou {@code TYPE.PLAT}). L'inscription reste rattachée à sa soirée (ADR-0009) : la veille pour un
 * petit-déjeuner, le jour même sinon.
 *
 * <p>Le lien magique le transporte aussi : le choix survit à l'ouverture du lien sur un autre appareil ou un
 * autre navigateur. Il ne contient aucune donnée personnelle. Ce n'est qu'une intention : la disponibilité est
 * revérifiée à la confirmation. Toute valeur est relue ici, jamais recopiée : pas de redirection ouverte.
 */
public record RegistrationChoice(LocalDate day, RegistrationType type, DishChoice dishChoice) {

    public static final String DAY_PARAM = "jour";
    public static final String CHOICE_PARAM = "choix";

    public RegistrationChoice {
        Objects.requireNonNull(day, "day");
        type.coveredPosts(dishChoice);
    }

    /** Choix d'une inscription désignée par sa soirée. */
    public static RegistrationChoice onEvening(LocalDate eveningDate, RegistrationType type, DishChoice dishChoice) {
        return new RegistrationChoice(type.activityDate(eveningDate), type, dishChoice);
    }

    /** Choix valide, ou vide si un paramètre manque ou ne correspond à rien de connu. */
    public static Optional<RegistrationChoice> parse(String day, String choice) {
        if (day == null || choice == null) {
            return Optional.empty();
        }
        try {
            var date = LocalDate.parse(day);
            var parts = choice.split("\\.", -1);
            if (parts.length > 2) {
                return Optional.empty();
            }
            var type = RegistrationType.valueOf(parts[0]);
            var dish = parts.length == 2 ? DishChoice.valueOf(parts[1]) : null;
            return Optional.of(new RegistrationChoice(date, type, dish));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Soirée qui portera l'inscription : celle de la veille pour un petit-déjeuner, celle du jour sinon. */
    public LocalDate eveningDate() {
        return type.eveningDate(day);
    }

    /** Valeur du paramètre {@code choix}. */
    public String choiceParam() {
        return choiceParam(type, dishChoice);
    }

    public static String choiceParam(RegistrationType type, DishChoice dishChoice) {
        return dishChoice == null ? type.name() : type.name() + "." + dishChoice.name();
    }

    public UriComponentsBuilder appendTo(UriComponentsBuilder uri) {
        return uri.queryParam(DAY_PARAM, day).queryParam(CHOICE_PARAM, choiceParam());
    }

    /** Chemin local suivi du choix, par exemple {@code /inscription/confirmer?jour=…&choix=…}. */
    public String appendTo(String path) {
        return appendTo(UriComponentsBuilder.fromPath(path)).encode().build().toUriString();
    }
}
