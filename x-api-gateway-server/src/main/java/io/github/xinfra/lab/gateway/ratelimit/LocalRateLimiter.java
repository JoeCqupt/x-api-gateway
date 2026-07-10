package io.github.xinfra.lab.gateway.ratelimit;

import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LocalRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Response> isAllowed(String key, long replenishRate, long burstCapacity) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(replenishRate, burstCapacity));
        long remaining = bucket.tryAcquire();
        if (remaining < 0) {
            long retryAfterMs = 1000L / Math.max(1, replenishRate);
            return Mono.just(new Response(false, 0, retryAfterMs));
        }
        return Mono.just(new Response(true, remaining, 0));
    }

    static class TokenBucket {
        private final long replenishRate;
        private final long burstCapacity;
        private final AtomicLong tokens;
        private final AtomicLong lastRefillNanos;

        TokenBucket(long replenishRate, long burstCapacity) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
            this.tokens = new AtomicLong(burstCapacity);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

        long tryAcquire() {
            refill();
            while (true) {
                long current = tokens.get();
                if (current <= 0) return -1;
                if (tokens.compareAndSet(current, current - 1)) {
                    return current - 1;
                }
            }
        }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillNanos.get();
            long elapsed = now - last;
            long tokensToAdd = elapsed * replenishRate / 1_000_000_000L;
            if (tokensToAdd > 0 && lastRefillNanos.compareAndSet(last, now)) {
                long current = tokens.get();
                long newTokens = Math.min(burstCapacity, current + tokensToAdd);
                tokens.set(newTokens);
            }
        }

        long getTokens() {
            refill();
            return tokens.get();
        }
    }
}
