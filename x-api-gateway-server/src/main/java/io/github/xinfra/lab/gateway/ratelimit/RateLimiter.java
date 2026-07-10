package io.github.xinfra.lab.gateway.ratelimit;

import reactor.core.publisher.Mono;

public interface RateLimiter {

    Mono<Response> isAllowed(String key, long replenishRate, long burstCapacity);

    class Response {
        private final boolean allowed;
        private final long remaining;
        private final long retryAfterMs;

        public Response(boolean allowed, long remaining, long retryAfterMs) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.retryAfterMs = retryAfterMs;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getRemaining() {
            return remaining;
        }

        public long getRetryAfterMs() {
            return retryAfterMs;
        }
    }
}
