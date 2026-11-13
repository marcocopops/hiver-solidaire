package com.marcantoinemeunier.hiversolidaire.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Planification de la purge (ADR-0014) : chaque jour à 4 h 30, heure de Paris (heure creuse, hors du changement d'heure),
 * et une fois au démarrage, pour rattraper une exécution manquée pendant un arrêt. Active par défaut
 * ({@code app.retention.scheduler.enabled}) ; coupée dans les tests, qui appellent {@link DataRetention#run()} avec
 * l'horloge réglable. Les décisions lisent l'horloge injectée ; le planificateur ne fait que réveiller la tâche.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "app.retention.scheduler.enabled", matchIfMissing = true)
class DataRetentionScheduling {

    @Bean
    RetentionTicker retentionTicker(DataRetention retention) {
        return new RetentionTicker(retention);
    }

    static class RetentionTicker {

        private static final Logger LOG = LoggerFactory.getLogger(RetentionTicker.class);

        private final DataRetention retention;

        RetentionTicker(DataRetention retention) {
            this.retention = retention;
        }

        @EventListener(ApplicationReadyEvent.class)
        void atStartup() {
            run();
        }

        @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Paris")
        void daily() {
            run();
        }

        private void run() {
            try {
                retention.run();
            } catch (RuntimeException e) {
                // Seule la classe : le message d'une exception SQL peut citer des valeurs.
                LOG.warn("Data retention run failed ({})", e.getClass().getSimpleName());
            }
        }
    }
}
