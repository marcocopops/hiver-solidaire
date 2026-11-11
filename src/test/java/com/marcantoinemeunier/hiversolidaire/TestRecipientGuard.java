package com.marcantoinemeunier.hiversolidaire;

import com.marcantoinemeunier.hiversolidaire.notification.RecipientGuard;

/**
 * Garde-fou des domaines réservés, modifiable pendant les tests. Désactivé par défaut : toutes les adresses de test sont
 * en {@code @example.org}. Un test qui l'active le désactive à la fin ({@link #reset()}).
 */
public final class TestRecipientGuard extends RecipientGuard {

    private volatile boolean blocking;

    public TestRecipientGuard() {
        super(true);
    }

    /** Comme en production : aucun envoi vers un domaine réservé. */
    public void block() {
        blocking = true;
    }

    public void reset() {
        blocking = false;
    }

    @Override
    public boolean allows(String email) {
        return !blocking || super.allowsOutsideReservedDomains(email);
    }
}
