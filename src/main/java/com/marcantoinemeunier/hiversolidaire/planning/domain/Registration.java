package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Inscription telle que les règles en ont besoin : type, plat éventuel et identifiant technique du bénévole
 * (R2). Aucune donnée personnelle. {@code volunteerId} est nul quand seul le calcul de capacité compte. Un bénévole peut
 * avoir plusieurs inscriptions sur une soirée, une par poste ({@link #ownPost()}, ADR-0011).
 */
public record Registration(RegistrationType type, DishChoice dishChoice, UUID volunteerId) {

    public Registration {
        Objects.requireNonNull(type, "type");
        type.coveredPosts(dishChoice);
    }

    public static Registration of(RegistrationType type) {
        return new Registration(type, null, null);
    }

    public static Registration of(RegistrationType type, DishChoice dishChoice) {
        return new Registration(type, dishChoice, null);
    }

    public static Registration by(UUID volunteerId, RegistrationType type, DishChoice dishChoice) {
        return new Registration(type, dishChoice, Objects.requireNonNull(volunteerId, "volunteerId"));
    }

    public Set<Post> coveredPosts() {
        return type.coveredPosts(dishChoice);
    }

    /**
     * Poste propre de l'inscription au sens de R2 (ADR-0011) : le plat pour qui prépare (chez soi ou en partageant),
     * la présence au dîner pour qui récupère, la nuit, ou le petit-déjeuner. Un bénévole ne prend pas deux fois le
     * même poste sur une soirée. Même découpage que l'index unique de la V14, sur {@code COALESCE(dish_choice, type)}.
     */
    public Post ownPost() {
        return switch (type) {
            case PREPARE_AND_SHARE, PREPARE_AT_HOME -> dishChoice.post();
            case PICK_UP_AND_SHARE -> Post.DINNER_PRESENCE;
            case NIGHT_AND_DINNER -> Post.NIGHT;
            case BREAKFAST -> Post.BREAKFAST;
        };
    }

    /**
     * R2 : deux inscriptions d'un même bénévole sur une même soirée sont incompatibles si elles prennent le même poste,
     * ou si l'une est une récupération et l'autre un autre poste du dîner : « Récupérer » ne se cumule qu'avec le
     * petit-déjeuner (avec sa propre préparation chez soi, ce serait « préparer et partager » ; avec la nuit, le
     * bénévole de nuit compterait dans les présences ; avec « préparer et partager », une personne seule compterait deux
     * présences). Les bénévoles ne sont pas comparés : c'est à l'appelant de ne comparer que ceux d'un même bénévole.
     */
    public boolean conflictsWith(Registration other) {
        return ownPost() == other.ownPost() || pickupBesideDinner(this, other) || pickupBesideDinner(other, this);
    }

    private static boolean pickupBesideDinner(Registration pickup, Registration other) {
        return pickup.type == RegistrationType.PICK_UP_AND_SHARE && other.type != RegistrationType.BREAKFAST;
    }
}
