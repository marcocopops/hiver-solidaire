package com.marcantoinemeunier.hiversolidaire.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Fuseau des règles métier : la veille, le jour même et la soirée passée s'entendent à l'heure de Paris. */
public final class ParisTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private ParisTime() {
    }

    public static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, ZONE);
    }
}
