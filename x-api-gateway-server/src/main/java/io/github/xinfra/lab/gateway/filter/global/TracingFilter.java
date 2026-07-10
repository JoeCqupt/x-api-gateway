package io.github.xinfra.lab.gateway.filter.global;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.filter.GatewayFilterChain;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Slf4j
@Order(-950)
public class TracingFilter implements GlobalGatewayFilter {

    public static final String TRACING_SPAN_ATTR = "gatewayTracingSpan";

    private final Tracer tracer;

    public TracingFilter(String serviceName) {
        this.tracer = GlobalOpenTelemetry.getTracer(serviceName);
    }

    public static boolean isAvailable() {
        try {
            Class.forName("io.opentelemetry.api.GlobalOpenTelemetry");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Context extractedContext = GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), exchange, HEADER_GETTER);

        String method = exchange.getRequest().method().name();
        String path = exchange.getRequest().fullPath();

        Span span = tracer.spanBuilder("gateway.request")
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", method)
                .setAttribute("http.url", path)
                .startSpan();

        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route != null) {
            span.setAttribute("http.route", route.getId());
        }

        exchange.getAttributes().put(TRACING_SPAN_ATTR, span);

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    span.setStatus(StatusCode.OK);
                    span.end();
                })
                .doOnError(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    span.end();
                });
    }

    private static final TextMapGetter<ServerWebExchange> HEADER_GETTER =
            new TextMapGetter<ServerWebExchange>() {
                @Override
                public Iterable<String> keys(ServerWebExchange exchange) {
                    return exchange.getRequest().requestHeaders().names();
                }

                @Override
                public String get(ServerWebExchange exchange, String key) {
                    return exchange.getRequest().requestHeaders().get(key);
                }
            };
}
