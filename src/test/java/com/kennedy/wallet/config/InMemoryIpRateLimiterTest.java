package com.kennedy.wallet.config;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryIpRateLimiterTest {
    @Test void enforcesLimitAndStartsANewWindow() {
        AtomicLong now = new AtomicLong(1_000);
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
            @Override public long millis() { return now.get(); }
        };
        InMemoryIpRateLimiter limiter = new InMemoryIpRateLimiter(clock, 2, 1000);
        assertTrue(limiter.allow("192.0.2.1"));
        assertTrue(limiter.allow("192.0.2.1"));
        assertFalse(limiter.allow("192.0.2.1"));
        now.addAndGet(1000);
        assertTrue(limiter.allow("192.0.2.1"));
    }
}
