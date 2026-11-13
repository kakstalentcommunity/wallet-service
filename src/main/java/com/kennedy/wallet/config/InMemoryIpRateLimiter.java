package com.kennedy.wallet.config;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryIpRateLimiter implements IpRateLimiter {
    private record Window(long startedAt, int requests) {}
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limit;
    private final long windowMillis;

    public InMemoryIpRateLimiter() { this(Clock.systemUTC(), 30, 60_000); }
    InMemoryIpRateLimiter(Clock clock, int limit, long windowMillis) {
        this.clock = clock; this.limit = limit; this.windowMillis = windowMillis;
    }

    @Override public boolean allow(String clientIp) {
        long now = clock.millis();
        Window next = windows.compute(clientIp, (key, previous) ->
                previous == null || now - previous.startedAt() >= windowMillis
                        ? new Window(now, 1) : new Window(previous.startedAt(), previous.requests() + 1));
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= windowMillis);
        return next.requests() <= limit;
    }
}
