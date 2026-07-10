package io.github.xinfra.lab.gateway.ratelimit;

import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public class SlidingWindowRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Response> isAllowed(String key, long replenishRate, long burstCapacity) {
        SlidingWindow window = windows.computeIfAbsent(key,
                k -> new SlidingWindow(burstCapacity));
        boolean allowed = window.tryAcquire();
        if (!allowed) {
            long retryAfterMs = 1000L / Math.max(1, replenishRate);
            return Mono.just(new Response(false, 0, retryAfterMs));
        }
        long remaining = Math.max(0, burstCapacity - window.count());
        return Mono.just(new Response(true, remaining, 0));
    }

    static class SlidingWindow {
        private final long maxRequests;
        private final ConcurrentLinkedDeque<Long> timestamps = new ConcurrentLinkedDeque<>();
        private final AtomicLong cleanupCounter = new AtomicLong(0);
        private static final long WINDOW_MS = 1000;

        SlidingWindow(long maxRequests) {
            this.maxRequests = maxRequests;
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            evictExpired(now);

            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }

        long count() {
            evictExpired(System.currentTimeMillis());
            return timestamps.size();
        }

        private void evictExpired(long now) {
            long cutoff = now - WINDOW_MS;
            while (true) {
                Long oldest = timestamps.peekFirst();
                if (oldest == null || oldest > cutoff) break;
                timestamps.pollFirst();
            }
        }
    }
}
