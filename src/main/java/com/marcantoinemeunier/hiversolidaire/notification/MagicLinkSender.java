package com.marcantoinemeunier.hiversolidaire.notification;

import java.net.URI;

/**
 * Envoi du lien de connexion. Implémentation SMTP aujourd'hui ; Brevo s'y branchera en S5. Le lien, le jeton et
 * l'adresse ne sont jamais journalisés.
 */
public interface MagicLinkSender {

    /** Faux tant qu'aucun canal d'envoi n'est configuré : la demande de lien affiche alors un message neutre. */
    boolean available();

    /**
     * Envoie le lien sans attendre la fin de l'envoi : la réponse à la demande prend le même temps, que
     * l'adresse soit connue ou non (menace M3).
     */
    void send(String email, URI link);
}
