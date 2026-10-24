package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.planning.domain.ParisTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge injectée, à l'heure de Paris, pour que les règles datées (R6, R7, rappels) soient testables. */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.system(ParisTime.ZONE);
    }
}
