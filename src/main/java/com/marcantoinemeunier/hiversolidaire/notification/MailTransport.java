package com.marcantoinemeunier.hiversolidaire.notification;

/**
 * Canal d'envoi des e-mails automatiques (confirmations, rappels, alertes, récap) : SMTP (Brevo en production, Mailpit
 * en développement). L'envoi est fait par la tâche planifiée, jamais dans une requête (ADR-0012).
 */
public interface MailTransport {

    /** Faux tant qu'aucun canal n'est configuré : les e-mails attendent, puis expirent. */
    boolean available();

    /**
     * Envoie l'e-mail et attend la réponse du serveur.
     *
     * @throws RuntimeException en cas d'échec ; son message peut contenir une adresse et ne doit jamais être journalisé
     */
    void send(OutgoingMail mail);
}
