package com.marcantoinemeunier.hiversolidaire.auth;

/**
 * Vérification anti-robot de la demande de lien (cahier des charges §8, ADR-0013, menaces M3, M4, M5) : le widget de
 * Cloudflare Turnstile remplit le champ {@code cf-turnstile-response} du formulaire, et le serveur vérifie ce jeton
 * auprès de {@code siteverify} avant de créer un lien ou d'envoyer un e-mail.
 */
public interface Turnstile {

    /** Issue d'une vérification. Seul {@link #PASSED} laisse partir le lien. */
    enum Outcome {
        PASSED,
        /** Jeton absent, invalide, expiré ou déjà utilisé. */
        FAILED,
        /** Cloudflare n'a pas répondu, ou pas lisiblement : on refuse plutôt que de laisser passer. */
        UNAVAILABLE
    }

    /** Faux sans clés (ou avec les clés de test hors localhost) : la demande de lien est alors indisponible. */
    boolean configured();

    /** Clé publique du widget, affichée dans la page. */
    String siteKey();

    /**
     * Vérifie le jeton du widget.
     *
     * @param remoteIp adresse IP du client, transmise par cloudflared ({@code getRemoteAddr()})
     */
    Outcome verify(String token, String remoteIp);
}
