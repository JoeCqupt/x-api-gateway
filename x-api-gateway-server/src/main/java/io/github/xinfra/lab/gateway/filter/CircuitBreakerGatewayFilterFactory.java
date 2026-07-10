package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Order(-100)
public class CircuitBreakerGatewayFilterFactory
        extends AbstractConfigurable<CircuitBreakerGatewayFilterFactory.Config>
        implements GatewayFilterFactory<CircuitBreakerGatewayFilterFactory.Config> {

    public static final String NAME = "CircuitBreaker";

    public CircuitBreakerGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

        return (exchange, chain) -> {
            String routeId = resolveRouteId(exchange);
            CircuitBreaker cb = breakers.computeIfAbsent(routeId,
                    k -> new CircuitBreaker(config));

            if (!cb.isCallPermitted()) {
                return Mono.error(new ErrorResponseException(
                        HttpResponseStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.CIRCUIT_BREAKER_OPEN));
            }

            return chain.filter(exchange)
                    .doOnSuccess(v -> cb.recordSuccess())
                    .doOnError(t -> cb.recordFailure());
        };
    }

    private static String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "default";
    }

    static class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }

        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicLong openedAt = new AtomicLong(0);
        private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
        private final AtomicInteger halfOpenPermitsUsed = new AtomicInteger(0);

        private final double failureRateThreshold;
        private final long waitDurationMs;
        private final int slidingWindowSize;
        private final int halfOpenPermits;

        private final long[] callTimestamps;
        private final boolean[] callResults;
        private final AtomicInteger writeIndex = new AtomicInteger(0);
        private final AtomicInteger callCount = new AtomicInteger(0);

        CircuitBreaker(Config config) {
            this.failureRateThreshold = config.getFailureRateThreshold();
            this.waitDurationMs = config.getWaitDurationInOpenStateMs();
            this.slidingWindowSize = config.getSlidingWindowSize();
            this.halfOpenPermits = config.getHalfOpenPermits();
            this.callTimestamps = new long[slidingWindowSize];
            this.callResults = new boolean[slidingWindowSize];
        }

        boolean isCallPermitted() {
            State current = state.get();
            if (current == State.CLOSED) {
                return true;
            }
            if (current == State.OPEN) {
                if (System.currentTimeMillis() - openedAt.get() >= waitDurationMs) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        halfOpenPermitsUsed.set(0);
                        halfOpenSuccesses.set(0);
                    }
                    return halfOpenPermitsUsed.incrementAndGet() <= halfOpenPermits;
                }
                return false;
            }
            return halfOpenPermitsUsed.incrementAndGet() <= halfOpenPermits;
        }

        void recordSuccess() {
            State current = state.get();
            if (current == State.HALF_OPEN) {
                int successes = halfOpenSuccesses.incrementAndGet();
                if (successes >= halfOpenPermits) {
                    transitionTo(State.CLOSED);
                }
            } else if (current == State.CLOSED) {
                recordCall(true);
            }
        }

        void recordFailure() {
            State current = state.get();
            if (current == State.HALF_OPEN) {
                transitionTo(State.OPEN);
            } else if (current == State.CLOSED) {
                recordCall(false);
            }
        }

        private synchronized void recordCall(boolean success) {
            int idx = writeIndex.getAndIncrement() % slidingWindowSize;
            callTimestamps[idx] = System.currentTimeMillis();
            callResults[idx] = success;
            int count = callCount.incrementAndGet();

            if (count >= slidingWindowSize) {
                int total = Math.min(count, slidingWindowSize);
                int failures = 0;
                for (int i = 0; i < total; i++) {
                    if (!callResults[i]) failures++;
                }
                double failureRate = (double) failures / total * 100.0;
                if (failureRate >= failureRateThreshold) {
                    transitionTo(State.OPEN);
                }
            }
        }

        private void transitionTo(State newState) {
            state.set(newState);
            if (newState == State.OPEN) {
                openedAt.set(System.currentTimeMillis());
            } else if (newState == State.CLOSED) {
                callCount.set(0);
                writeIndex.set(0);
            }
        }

        State getState() {
            return state.get();
        }
    }

    @Data
    public static class Config {
        private double failureRateThreshold = 50.0;
        private long waitDurationInOpenStateMs = 30000;
        private int slidingWindowSize = 100;
        private int halfOpenPermits = 5;
    }
}
