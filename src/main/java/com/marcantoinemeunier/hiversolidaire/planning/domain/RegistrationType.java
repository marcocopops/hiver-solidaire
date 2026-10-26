package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Types d'inscription repris de 2025-2026 (cahier des charges, §5). */
public enum RegistrationType {
    /** Préparer et partager le dîner : 1 préparation + 1 présence. */
    PREPARE_AND_SHARE(true, EnumSet.of(Post.DINNER_PRESENCE)),
    /** Préparer le dîner chez soi et le tenir à disposition : 1 préparation. */
    PREPARE_AT_HOME(true, EnumSet.noneOf(Post.class)),
    /** Récupérer le dîner et le partager : 1 présence + récupération d'un repas préparé chez soi (R4). */
    PICK_UP_AND_SHARE(false, EnumSet.of(Post.DINNER_PRESENCE)),
    /** Passer la nuit et partager le dîner : 1 nuit, qui ne compte pas dans les présences au dîner. */
    NIGHT_AND_DINNER(false, EnumSet.of(Post.NIGHT)),
    /** Fournir et partager le petit-déjeuner. */
    BREAKFAST(false, EnumSet.of(Post.BREAKFAST));

    private final boolean preparesDish;
    private final Set<Post> fixedPosts;

    RegistrationType(boolean preparesDish, Set<Post> fixedPosts) {
        this.preparesDish = preparesDish;
        this.fixedPosts = fixedPosts;
    }

    /** Vrai si le type demande de choisir entre plat principal et accompagnements (R3). */
    public boolean preparesDish() {
        return preparesDish;
    }

    /** Jour de l'activité (ADR-0009) : le lendemain matin pour le petit-déjeuner, le soir même sinon. */
    public LocalDate activityDate(LocalDate eveningDate) {
        return this == BREAKFAST ? eveningDate.plusDays(1) : eveningDate;
    }

    /** Soirée qui porte cette activité le jour {@code day} : l'inverse de {@link #activityDate(LocalDate)}. */
    public LocalDate eveningDate(LocalDate day) {
        return this == BREAKFAST ? day.minusDays(1) : day;
    }

    /** Postes couverts ; {@code dishChoice} est obligatoire si le type prépare un plat, interdit sinon. */
    public Set<Post> coveredPosts(DishChoice dishChoice) {
        if (preparesDish != (dishChoice != null)) {
            throw new IllegalArgumentException(preparesDish
                    ? name() + " requires a dish choice"
                    : name() + " does not accept a dish choice");
        }
        var posts = EnumSet.noneOf(Post.class);
        posts.addAll(fixedPosts);
        if (dishChoice != null) {
            posts.add(dishChoice.post());
        }
        return posts;
    }
}
