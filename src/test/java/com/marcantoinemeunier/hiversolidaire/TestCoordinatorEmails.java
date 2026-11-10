package com.marcantoinemeunier.hiversolidaire;

import com.marcantoinemeunier.hiversolidaire.auth.CoordinatorEmails;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Liste des coordinateurs modifiable pendant les tests (en production : {@code APP_COORDINATOR_EMAILS}). */
public final class TestCoordinatorEmails extends CoordinatorEmails {

    private final Set<String> emails = ConcurrentHashMap.newKeySet();

    public TestCoordinatorEmails() {
        super("");
    }

    public void add(String email) {
        emails.add(email);
    }

    public void remove(String email) {
        emails.remove(email);
    }

    @Override
    public List<String> all() {
        return emails.stream().sorted().toList();
    }

    /** À appeler à la fin d'un test qui compte les destinataires : les coordinateurs des autres tests restent sinon. */
    public void clear() {
        emails.clear();
    }

    @Override
    public boolean contains(String normalizedEmail) {
        return emails.contains(normalizedEmail);
    }
}
