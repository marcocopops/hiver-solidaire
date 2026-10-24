package com.marcantoinemeunier.hiversolidaire.planning.domain;

/** R3 Préparation : qui prépare choisit le plat principal ou les accompagnements. */
public enum DishChoice {
    MAIN_DISH(Post.MAIN_DISH),
    SIDES(Post.SIDES);

    private final Post post;

    DishChoice(Post post) {
        this.post = post;
    }

    public Post post() {
        return post;
    }
}
