package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

public class DefaultGatewayFilterChain implements GatewayFilterChain {
    private final GatewayFilter[] filters;
    private int index = 0;

    public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
        this.filters = filters.toArray(new GatewayFilter[0]);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
        if (index < filters.length) {
            return filters[index++].filter(exchange, this);
        } else {
            return Mono.empty();
        }
    }
}
