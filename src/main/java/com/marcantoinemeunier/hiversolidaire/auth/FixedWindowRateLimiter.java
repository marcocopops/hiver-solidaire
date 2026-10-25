package com.marcantoinemeunier.hiversolidaire.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limiteur à fenêtre fixe, en mémoire : au plus {@code limit} demandes par clé et par fenêtre.
 *
 * <p>Suffisant pour une instance unique (cahier des charges §9) : les compteurs sont perdus au redémarrage et ne
 * sont pas partagés entre instances. Cloudflare limite aussi le débit en amont (§8). Si l'appli passe un jour à
 * plusieurs instances, il faudra un stockage partagé (base ou Redis).
 */
final class FixedWindowRateLimiter {

    /** Au-delà, les fenêtres terminées sont purgées : la mémoire reste bornée face à des clés toujours nouvelles. */
    private static final int PURGE_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {
    }

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Compte une demande et dit si elle reste sous la limite. */
    boolean tryAcquire(String key) {
        var now = clock.instant();
        if (windows.size() > PURGE_THRESHOLD) {
            windows.values().removeIf(existing -> expired(existing, now));
        }
        var updated = windows.compute(key, (k, existing) -> existing == null || expired(existing, now)
                ? new Window(now, 1)
                : new Window(existing.start(), existing.count() + 1));
        return updated.count() <= limit;
    }

    private boolean expired(Window existing, Instant now) {
        return !now.isBefore(existing.start().plus(window));
    }
}
