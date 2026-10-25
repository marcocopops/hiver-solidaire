package com.marcantoinemeunier.hiversolidaire;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Horloge réglable, placée pendant la saison d'exemple (inscriptions ouvertes), et double de l'envoi de lien.
 * Chaque test qui touche à l'horloge la remet à {@link #DURING_SEASON}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockAndMailConfiguration {

    /** Lundi 4 janvier 2027 à 10 h, heure de Paris : saison d'exemple ouverte, première soirée à venir. */
    public static final Instant DURING_SEASON = LocalDateTime.of(2027, 1, 4, 10, 0).atZone(ParisTime.ZONE).toInstant();

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(DURING_SEASON, ParisTime.ZONE);
    }

    @Bean
    @Primary
    public RecordingMagicLinkSender recordingMagicLinkSender() {
        return new RecordingMagicLinkSender();
    }
}
