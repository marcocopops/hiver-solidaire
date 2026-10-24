package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Besoins d'une saison, un par poste. Paramétrables : la source de vérité est la base, pas ce code. */
public final class Needs {

    private final Map<Post, Need> byPost;

    private Needs(Map<Post, Need> byPost) {
        this.byPost = byPost;
    }

    public static Needs of(Collection<Need> needs) {
        var byPost = new EnumMap<Post, Need>(Post.class);
        for (Need need : needs) {
            if (byPost.put(need.post(), need) != null) {
                throw new IllegalArgumentException("Duplicate need for " + need.post());
            }
        }
        if (byPost.size() != Post.values().length) {
            throw new IllegalArgumentException("Every post must have a need");
        }
        return new Needs(byPost);
    }

    /** Besoins de la saison 2025-2026 (§5), utilisés par la saison d'exemple. */
    public static Needs defaults() {
        return of(List.of(
                new Need(Post.MAIN_DISH, 1, 1),
                new Need(Post.SIDES, 1, 1),
                new Need(Post.DINNER_PRESENCE, 2, 2),
                new Need(Post.NIGHT, 1, 2),
                new Need(Post.BREAKFAST, 1, 1)));
    }

    public Need of(Post post) {
        return byPost.get(post);
    }
}
