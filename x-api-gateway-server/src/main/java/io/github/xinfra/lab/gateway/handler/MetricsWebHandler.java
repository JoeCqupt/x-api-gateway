package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

public class MetricsWebHandler extends WebHandlerDecorator {

    private static final String START_TIME_ATTR = "gatewayStartTime";

    private final MetricsCollector metricsCollector;

    public MetricsWebHandler(WebHandler delegate, MetricsCollector metricsCollector) {
        super(delegate);
        this.metricsCollector = metricsCollector;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());
        metricsCollector.incrementGauge("x_gateway_active_requests");

        return super.handle(exchange)
                .doFinally(signalType -> {
                    metricsCollector.decrementGauge("x_gateway_active_requests");

                    Long startTime = exchange.getAttribute(START_TIME_ATTR);
                    if (startTime == null) return;

                    long duration = System.currentTimeMillis() - startTime;
                    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                    String routeId = route != null ? route.getId() : "unknown";
                    int statusCode = exchange.getResponse().status() != null
                            ? exchange.getResponse().status().code() : 0;
                    String method = exchange.getRequest().method().name();

                    metricsCollector.recordRequest(routeId, statusCode, method, duration);
                });
    }
}
