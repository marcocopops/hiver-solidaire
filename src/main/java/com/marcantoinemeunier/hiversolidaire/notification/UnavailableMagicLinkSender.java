package com.marcantoinemeunier.hiversolidaire.notification;

import java.net.URI;

/** Aucun canal d'envoi configuré (production avant S5) : l'appli démarre, la connexion est indisponible. */
class UnavailableMagicLinkSender implements MagicLinkSender {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void send(String email, URI link) {
        throw new IllegalStateException("No mail channel is configured");
    }
}
