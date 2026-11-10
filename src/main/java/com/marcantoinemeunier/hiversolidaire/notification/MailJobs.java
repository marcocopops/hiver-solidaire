package com.marcantoinemeunier.hiversolidaire.notification;

import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tâche des e-mails automatiques (ADR-0012), lancée toutes les 30 secondes et au démarrage
 * ({@link MailSchedulingConfig}) : crée le rappel et le récap quand leur heure est passée, puis envoie tout ce qui est
 * dû. Elle ne garde aucun état en mémoire : après un redémarrage, elle reprend à partir de la table d'envois, sans rien
 * doubler. Une seule instance de l'appli : un verrou local suffit pour ne pas lancer deux tours à la fois.
 */
@Component
public class MailJobs {

    private static final Logger LOG = LoggerFactory.getLogger(MailJobs.class);

    private final ReentrantLock running = new ReentrantLock();
    private final MailPlanner planner;
    private final MailDispatcher dispatcher;

    MailJobs(MailPlanner planner, MailDispatcher dispatcher) {
        this.planner = planner;
        this.dispatcher = dispatcher;
    }

    /** Un tour : création de ce qui est à l'heure, puis envoi de ce qui est dû. Sans effet si un tour est en cours. */
    public void tick() {
        if (!running.tryLock()) {
            return;
        }
        try {
            planner.planDue();
            dispatcher.dispatchDue();
        } catch (RuntimeException e) {
            LOG.warn("Mail jobs failed ({})", e.getClass().getSimpleName());
        } finally {
            running.unlock();
        }
    }

    /**
     * Développement seulement ({@code APP_MAIL_DEV_RUN_NOW}) : crée tout de suite, sans attendre leur heure, le rappel
     * de la carte du lendemain et le récap du jour, puis envoie.
     */
    void runNowForDevelopment() {
        running.lock();
        try {
            planner.planNow();
            dispatcher.dispatchDue();
        } finally {
            running.unlock();
        }
    }
}
