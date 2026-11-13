package com.marcantoinemeunier.hiversolidaire;

import com.marcantoinemeunier.hiversolidaire.auth.Turnstile;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Double de Turnstile : jamais d'appel à Cloudflare en test. Par défaut configuré, il accepte le jeton factice
 * {@link Browser#TURNSTILE_TOKEN} et refuse tout autre jeton. Un test qui change sa réponse appelle {@link #reset()}
 * à la fin : le contexte, et donc ce double, est partagé entre les classes de test.
 */
public final class StubTurnstile implements Turnstile {

    public static final String SITE_KEY = "1x00000000000000000000AA";

    /** Un appel à {@code siteverify} : le jeton et l'adresse IP du client. */
    public record Call(String token, String remoteIp) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile Outcome forced;
    private volatile boolean configured = true;

    @Override
    public boolean configured() {
        return configured;
    }

    @Override
    public String siteKey() {
        return SITE_KEY;
    }

    @Override
    public Outcome verify(String token, String remoteIp) {
        calls.add(new Call(token, remoteIp));
        if (forced != null) {
            return forced;
        }
        return Browser.TURNSTILE_TOKEN.equals(token) ? Outcome.PASSED : Outcome.FAILED;
    }

    /** Toutes les vérifications suivantes donnent cette issue. */
    public void respondWith(Outcome outcome) {
        forced = outcome;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public List<Call> callsFrom(String remoteIp) {
        return calls.stream().filter(call -> remoteIp.equals(call.remoteIp())).toList();
    }

    public void reset() {
        forced = null;
        configured = true;
    }
}
