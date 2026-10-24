package com.marcantoinemeunier.hiversolidaire.planning.persistence;

import com.marcantoinemeunier.hiversolidaire.planning.domain.Need;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
class NeedEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(name = "post", nullable = false)
    private Post post;

    @Column(name = "minimum", nullable = false)
    private int minimum;

    @Column(name = "maximum", nullable = false)
    private int maximum;

    protected NeedEmbeddable() {
    }

    Need toDomain() {
        return new Need(post, minimum, maximum);
    }
}
