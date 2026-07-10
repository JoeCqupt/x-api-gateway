package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodRoutePredicateFactory
        extends AbstractConfigurable<MethodRoutePredicateFactory.Config>
        implements RoutePredicateFactory<MethodRoutePredicateFactory.Config> {

    public static final String NAME = "Method";

    public MethodRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        Set<String> methods = config.getMethods().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return exchange -> methods.contains(exchange.getRequest().method().name().toUpperCase());
    }

    @Data
    public static class Config {
        private List<String> methods;
    }
}
