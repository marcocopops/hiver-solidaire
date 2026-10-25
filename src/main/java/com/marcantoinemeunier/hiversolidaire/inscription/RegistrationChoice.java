package com.marcantoinemeunier.hiversolidaire.inscription;

import com.marcantoinemeunier.hiversolidaire.planning.domain.DishChoice;
import com.marcantoinemeunier.hiversolidaire.planning.domain.RegistrationType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Choix fait sur le planning (soirée, type, plat), porté de page en page par deux paramètres : {@code soiree}
 * (date du soir J, unique dans la saison : aucun identifiant technique sur les pages publiques) et {@code choix}
 * ({@code TYPE} ou {@code TYPE.PLAT}).
 *
 * <p>Le lien magique le transporte aussi : le choix survit à l'ouverture du lien sur un autre appareil ou un
 * autre navigateur. Il ne contient aucune donnée personnelle. Ce n'est qu'une intention : la disponibilité est
 * revérifiée à la confirmation. Toute valeur est relue ici, jamais recopiée : pas de redirection ouverte.
 */
public record RegistrationChoice(LocalDate eveningDate, RegistrationType type, DishChoice dishChoice) {

    public static final String EVENING_PARAM = "soiree";
    public static final String CHOICE_PARAM = "choix";

    public RegistrationChoice {
        Objects.requireNonNull(eveningDate, "eveningDate");
        type.coveredPosts(dishChoice);
    }

    /** Choix valide, ou vide si un paramètre manque ou ne correspond à rien de connu. */
    public static Optional<RegistrationChoice> parse(String evening, String choice) {
        if (evening == null || choice == null) {
            return Optional.empty();
        }
        try {
            var eveningDate = LocalDate.parse(evening);
            var parts = choice.split("\\.", -1);
            if (parts.length > 2) {
                return Optional.empty();
            }
            var type = RegistrationType.valueOf(parts[0]);
            var dish = parts.length == 2 ? DishChoice.valueOf(parts[1]) : null;
            return Optional.of(new RegistrationChoice(eveningDate, type, dish));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Valeur du paramètre {@code choix}. */
    public String choiceParam() {
        return choiceParam(type, dishChoice);
    }

    public static String choiceParam(RegistrationType type, DishChoice dishChoice) {
        return dishChoice == null ? type.name() : type.name() + "." + dishChoice.name();
    }

    public UriComponentsBuilder appendTo(UriComponentsBuilder uri) {
        return uri.queryParam(EVENING_PARAM, eveningDate).queryParam(CHOICE_PARAM, choiceParam());
    }

    /** Chemin local suivi du choix, par exemple {@code /inscription/confirmer?soiree=…&choix=…}. */
    public String appendTo(String path) {
        return appendTo(UriComponentsBuilder.fromPath(path)).encode().build().toUriString();
    }
}
