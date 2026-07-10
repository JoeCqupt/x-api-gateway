package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.commons.Ordered;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class OrderedGatewayFilter implements GatewayFilter, Ordered {

    private final GatewayFilter delegate;
    private final int order;

    public OrderedGatewayFilter(GatewayFilter delegate, int order) {
        this.delegate = delegate;
        this.order = order;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return delegate.filter(exchange, chain);
    }

    @Override
    public int getOrder() {
        return order;
    }

    public GatewayFilter getDelegate() {
        return delegate;
    }
}
