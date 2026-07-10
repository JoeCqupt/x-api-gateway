package io.github.xinfra.lab.gateway.route;

import io.github.xinfra.lab.gateway.endpoint.Endpoint;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.predicate.RoutePredicate;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface Route {

    String getId();

    RoutePredicate<ServerWebExchange> getPredicate();

    List<GatewayFilter> getFilters();

    Endpoint getEndpoint();

    default Map<String, Object> getMetadata() {
        return Collections.emptyMap();
    }
}
