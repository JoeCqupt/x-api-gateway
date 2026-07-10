package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Order(-600)
public class CorsGatewayFilterFactory
        extends AbstractConfigurable<CorsGatewayFilterFactory.Config>
        implements GatewayFilterFactory<CorsGatewayFilterFactory.Config> {

    public static final String NAME = "Cors";

    public CorsGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        if (config == null) {
            config = new Config();
        }
        Config corsConfig = config;

        return (exchange, chain) -> {
            String origin = exchange.getRequest().requestHeaders().get("Origin");
            if (origin == null) {
                return chain.filter(exchange);
            }

            if (!isOriginAllowed(origin, corsConfig.getAllowedOrigins())) {
                return chain.filter(exchange);
            }

            if (HttpMethod.OPTIONS.equals(exchange.getRequest().method())) {
                addCorsHeaders(exchange, origin, corsConfig);
                exchange.getResponse().status(HttpResponseStatus.NO_CONTENT);
                return exchange.getResponse().send(Mono.empty()).then();
            }

            return chain.filter(exchange).then(Mono.fromRunnable(() ->
                    addCorsHeaders(exchange, origin, corsConfig)));
        };
    }

    private void addCorsHeaders(io.github.xinfra.lab.gateway.server.ServerWebExchange exchange,
                                String origin, Config config) {
        exchange.getResponse().addHeader("Access-Control-Allow-Origin", origin);

        if (config.getAllowedMethods() != null && !config.getAllowedMethods().isEmpty()) {
            exchange.getResponse().addHeader("Access-Control-Allow-Methods",
                    String.join(", ", config.getAllowedMethods()));
        }
        if (config.getAllowedHeaders() != null && !config.getAllowedHeaders().isEmpty()) {
            exchange.getResponse().addHeader("Access-Control-Allow-Headers",
                    String.join(", ", config.getAllowedHeaders()));
        }
        if (config.getExposeHeaders() != null && !config.getExposeHeaders().isEmpty()) {
            exchange.getResponse().addHeader("Access-Control-Expose-Headers",
                    String.join(", ", config.getExposeHeaders()));
        }
        if (config.isAllowCredentials()) {
            exchange.getResponse().addHeader("Access-Control-Allow-Credentials", "true");
        }
        if (config.getMaxAge() > 0) {
            exchange.getResponse().addHeader("Access-Control-Max-Age",
                    String.valueOf(config.getMaxAge()));
        }
    }

    private boolean isOriginAllowed(String origin, List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) return false;
        for (String allowed : allowedOrigins) {
            if ("*".equals(allowed) || allowed.equalsIgnoreCase(origin)) {
                return true;
            }
        }
        return false;
    }

    @Data
    public static class Config {
        private List<String> allowedOrigins = Arrays.asList("*");
        private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = Arrays.asList("*");
        private List<String> exposeHeaders;
        private boolean allowCredentials = false;
        private long maxAge = 3600;
    }
}
