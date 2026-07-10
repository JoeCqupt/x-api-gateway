package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.util.regex.Pattern;

public class PathRoutePredicateFactory
        extends AbstractConfigurable<PathRoutePredicateFactory.PatternConfig>
        implements RoutePredicateFactory<PathRoutePredicateFactory.PatternConfig> {

    public static final String NAME = "Path";

    public PathRoutePredicateFactory() {
        super(PatternConfig.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(PatternConfig config) {
        Pattern p = Pattern.compile(config.getPattern());
        return exchange -> p.matcher(exchange.getRequest().fullPath()).matches();
    }

    @Data
    public static class PatternConfig {
        private String pattern;
    }

}
