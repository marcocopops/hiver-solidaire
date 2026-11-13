package com.marcantoinemeunier.hiversolidaire.notification;

/**
 * Garde-fou d'envoi (cahier des charges §7, modèle de menaces M30) : aucun e-mail vers un domaine réservé aux tests
 * ({@link ReservedDomains}). Appliqué avant l'envoi du lien de connexion et par la tâche des e-mails automatiques, qui
 * abandonne alors la ligne ({@code RESERVED_DOMAIN}) sans erreur.
 *
 * <p>Désactivable pour le développement seulement ({@code APP_MAIL_ALLOW_RESERVED_DOMAINS=true}) : Mailpit reçoit alors
 * les adresses factices {@code @example.org}. Jamais en production.
 */
public class RecipientGuard {

    private final boolean allowReservedDomains;

    public RecipientGuard(boolean allowReservedDomains) {
        this.allowReservedDomains = allowReservedDomains;
    }

    /** Vrai si l'appli peut écrire à cette adresse. */
    public boolean allows(String email) {
        return allowReservedDomains || allowsOutsideReservedDomains(email);
    }

    /** La règle de production, indépendamment de la propriété de développement. */
    protected final boolean allowsOutsideReservedDomains(String email) {
        return !ReservedDomains.isReserved(email);
    }
}
