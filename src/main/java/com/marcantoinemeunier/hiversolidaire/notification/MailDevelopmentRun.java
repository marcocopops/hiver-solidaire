package com.marcantoinemeunier.hiversolidaire.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Développement seulement : {@code APP_MAIL_DEV_RUN_NOW=true} crée et envoie au démarrage le rappel de la carte du
 * lendemain et le récap du jour, sans attendre 18 h ni 20 h, pour les voir dans Mailpit. Aucun endpoint : rien n'est
 * exposé. Absent par défaut, la variable n'est jamais définie en production ({@code deploy/README.md}).
 */
@Component
@ConditionalOnBooleanProperty(name = "app.mail.dev-run-now")
class MailDevelopmentRun {

    private static final Logger LOG = LoggerFactory.getLogger(MailDevelopmentRun.class);

    private final MailJobs jobs;

    MailDevelopmentRun(MailJobs jobs) {
        this.jobs = jobs;
    }

    @EventListener(ApplicationReadyEvent.class)
    void runNow() {
        LOG.warn("Reminder and recap are created now (APP_MAIL_DEV_RUN_NOW): development only");
        jobs.runNowForDevelopment();
    }
}
