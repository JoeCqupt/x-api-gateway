package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.commons.Ordered;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

public class ConfigurableGatewayFilter<C> implements GatewayFilter, Ordered {

    private final AtomicReference<C> configRef;
    private final GatewayFilter delegate;
    private final int order;
    private final String filterName;

    public ConfigurableGatewayFilter(AtomicReference<C> configRef, GatewayFilter delegate,
            int order, String filterName) {
        this.configRef = configRef;
        this.delegate = delegate;
        this.order = order;
        this.filterName = filterName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return delegate.filter(exchange, chain);
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void updateConfig(C newConfig) {
        configRef.set(newConfig);
    }

    public C getConfig() {
        return configRef.get();
    }

    public String getFilterName() {
        return filterName;
    }

    public GatewayFilter getDelegate() {
        return delegate;
    }
}
