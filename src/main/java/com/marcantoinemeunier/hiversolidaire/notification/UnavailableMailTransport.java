package com.marcantoinemeunier.hiversolidaire.notification;

/** Aucun canal d'envoi configuré : les e-mails restent en attente dans la table, puis expirent. */
class UnavailableMailTransport implements MailTransport {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void send(OutgoingMail mail) {
        throw new IllegalStateException("No mail channel is configured");
    }
}
