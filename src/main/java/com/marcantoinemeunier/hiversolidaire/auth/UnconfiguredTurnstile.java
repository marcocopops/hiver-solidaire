package com.marcantoinemeunier.hiversolidaire.auth;

/** Pas de clés Turnstile utilisables : la demande de lien est indisponible, et rien n'est jamais vérifié. */
class UnconfiguredTurnstile implements Turnstile {

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public String siteKey() {
        return "";
    }

    @Override
    public Outcome verify(String token, String remoteIp) {
        return Outcome.UNAVAILABLE;
    }
}
