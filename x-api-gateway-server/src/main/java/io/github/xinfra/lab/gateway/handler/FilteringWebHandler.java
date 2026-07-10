package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.commons.OrderedAwareComparator;
import io.github.xinfra.lab.gateway.filter.DefaultGatewayFilterChain;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.filter.global.GlobalGatewayFilter;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

public class FilteringWebHandler implements WebHandler {

    private final List<GatewayFilter> preSortedBaseFilters;

    public FilteringWebHandler(List<GlobalGatewayFilter> globalFilters) {
        this(globalFilters, Collections.emptyList());
    }

    public FilteringWebHandler(List<GlobalGatewayFilter> globalFilters, List<GatewayFilter> defaultFilters) {
        List<GatewayFilter> base = new ArrayList<>(defaultFilters);
        base.addAll(globalFilters);
        OrderedAwareComparator.sort(base);
        this.preSortedBaseFilters = base;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
        List<GatewayFilter> routeFilters = route.getFilters();

        if (routeFilters == null || routeFilters.isEmpty()) {
            return new DefaultGatewayFilterChain(preSortedBaseFilters).filter(exchange);
        }

        List<GatewayFilter> combinedFilters = new ArrayList<>(preSortedBaseFilters.size() + routeFilters.size());
        combinedFilters.addAll(preSortedBaseFilters);
        combinedFilters.addAll(routeFilters);
        OrderedAwareComparator.sort(combinedFilters);

        return new DefaultGatewayFilterChain(combinedFilters).filter(exchange);
    }

}
