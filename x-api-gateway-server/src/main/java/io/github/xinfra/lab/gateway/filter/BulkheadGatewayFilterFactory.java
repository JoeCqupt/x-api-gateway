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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Order(-150)
public class BulkheadGatewayFilterFactory
        extends AbstractConfigurable<BulkheadGatewayFilterFactory.Config>
        implements GatewayFilterFactory<BulkheadGatewayFilterFactory.Config> {

    public static final String NAME = "Bulkhead";

    public BulkheadGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

        return (exchange, chain) -> {
            String routeId = resolveRouteId(exchange);
            Semaphore semaphore = semaphores.computeIfAbsent(routeId,
                    k -> new Semaphore(config.getMaxConcurrentCalls()));

            boolean acquired;
            try {
                acquired = semaphore.tryAcquire(config.getMaxWaitMs(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Mono.error(new ErrorResponseException(
                        HttpResponseStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.SERVICE_UNAVAILABLE));
            }

            if (!acquired) {
                return Mono.error(new ErrorResponseException(
                        HttpResponseStatus.TOO_MANY_REQUESTS,
                        ErrorCode.RATE_LIMITED));
            }

            return chain.filter(exchange)
                    .doFinally(signal -> semaphore.release());
        };
    }

    private static String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "default";
    }

    @Data
    public static class Config {
        private int maxConcurrentCalls = 25;
        private long maxWaitMs = 0;
    }
}
