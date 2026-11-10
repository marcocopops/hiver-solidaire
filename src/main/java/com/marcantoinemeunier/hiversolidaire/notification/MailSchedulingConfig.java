package com.marcantoinemeunier.hiversolidaire.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Planification des e-mails (ADR-0012). Active par défaut ({@code app.mail.scheduler.enabled}) ; coupée dans les tests,
 * qui appellent {@link MailJobs#tick()} eux-mêmes avec l'horloge réglable. Les décisions lisent l'horloge injectée ;
 * le planificateur ne fait que réveiller la tâche.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "app.mail.scheduler.enabled", matchIfMissing = true)
class MailSchedulingConfig {

    @Bean
    MailTicker mailTicker(MailJobs jobs) {
        return new MailTicker(jobs);
    }

    static class MailTicker {

        private final MailJobs jobs;

        MailTicker(MailJobs jobs) {
            this.jobs = jobs;
        }

        /** Le premier tour part au démarrage : ce qui n'est pas parti pendant l'arrêt est rattrapé s'il est encore temps. */
        @Scheduled(fixedDelayString = "${app.mail.scheduler.delay:PT30S}", initialDelayString = "PT5S")
        void tick() {
            jobs.tick();
        }
    }
}
