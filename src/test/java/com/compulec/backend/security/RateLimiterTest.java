package com.compulec.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };

    @Test
    void blocksAfterLimit_ThenResetsAfterWindow() {
        RateLimiter limiter = new RateLimiter(clock);
        for (int i = 0; i < 5; i++) {
            assertEquals(0, limiter.tryAcquire("login:1.2.3.4", 5, 60_000));
        }
        long retryAfter = limiter.tryAcquire("login:1.2.3.4", 5, 60_000);
        assertEquals(60, retryAfter);

        // Une autre IP n'est pas concernée
        assertEquals(0, limiter.tryAcquire("login:5.6.7.8", 5, 60_000));

        now.addAndGet(60_000);
        assertEquals(0, limiter.tryAcquire("login:1.2.3.4", 5, 60_000));
    }
}
