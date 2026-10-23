package com.compulec.backend.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compteur à fenêtre fixe, en mémoire, par clé (ex. "login:192.168.1.20").
 * Suffisant pour un serveur unique ; pour plusieurs instances, utiliser un stockage partagé (Redis).
 */
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long startMs, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Enregistre une requête.
     *
     * @return 0 si elle est autorisée, sinon le nombre de secondes à attendre
     */
    public long tryAcquire(String key, int limit, long windowMs) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMs() > windowMs * 4);
        }
        Window w = windows.compute(key, (k, current) ->
                current == null || now - current.startMs() >= windowMs
                        ? new Window(now, 1)
                        : new Window(current.startMs(), current.count() + 1));
        if (w.count() <= limit) {
            return 0;
        }
        return Math.max(1, (w.startMs() + windowMs - now + 999) / 1000);
    }
}
