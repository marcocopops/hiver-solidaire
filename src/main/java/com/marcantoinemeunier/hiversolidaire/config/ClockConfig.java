package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge injectée, à l'heure de Paris, pour que les règles datées (R6, R7, rappels) soient testables. */
@Configuration
class ClockConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ClockConfig.class);

    /**
     * {@code APP_CLOCK_OFFSET} (développement seulement, durée ISO 8601 comme {@code P70D}) décale l'horloge pour
     * essayer l'inscription avant l'ouverture de la saison. Absent par défaut, et jamais défini en production.
     */
    @Bean
    Clock clock(@Value("${APP_CLOCK_OFFSET:}") String offset) {
        var system = Clock.system(ParisTime.ZONE);
        if (offset.isBlank()) {
            return system;
        }
        var duration = Duration.parse(offset);
        LOG.warn("Application clock is shifted by {} (APP_CLOCK_OFFSET): development only", duration);
        return Clock.offset(system, duration);
    }
}
