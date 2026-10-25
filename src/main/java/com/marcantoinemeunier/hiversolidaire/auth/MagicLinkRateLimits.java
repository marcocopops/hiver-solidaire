package com.marcantoinemeunier.hiversolidaire.auth;

import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Limites des demandes de lien (cahier des charges §8, menaces M3 et M5), en plus de Turnstile (S5) et de
 * Cloudflare : 3 par adresse toutes les 15 minutes, 10 par IP par heure. En mémoire : voir
 * {@link FixedWindowRateLimiter}.
 */
@Component
class MagicLinkRateLimits {

    static final int PER_EMAIL = 3;
    static final Duration EMAIL_WINDOW = Duration.ofMinutes(15);
    static final int PER_IP = 10;
    static final Duration IP_WINDOW = Duration.ofHours(1);

    private final FixedWindowRateLimiter byEmail;
    private final FixedWindowRateLimiter byIp;

    MagicLinkRateLimits(Clock clock) {
        this.byEmail = new FixedWindowRateLimiter(PER_EMAIL, EMAIL_WINDOW, clock);
        this.byIp = new FixedWindowRateLimiter(PER_IP, IP_WINDOW, clock);
    }

    /**
     * Adresse IP du client : {@code getRemoteAddr()} après {@code server.forward-headers-strategy: native}, qui
     * prend l'IP transmise par cloudflared (seul proxy capable de joindre l'appli).
     */
    boolean allowIp(String remoteAddress) {
        return byIp.tryAcquire(remoteAddress);
    }

    boolean allowEmail(String normalizedEmail) {
        return byEmail.tryAcquire(normalizedEmail);
    }
}
