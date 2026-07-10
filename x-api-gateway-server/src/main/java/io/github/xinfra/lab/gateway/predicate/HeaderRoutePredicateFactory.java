package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.util.regex.Pattern;

public class HeaderRoutePredicateFactory
        extends AbstractConfigurable<HeaderRoutePredicateFactory.Config>
        implements RoutePredicateFactory<HeaderRoutePredicateFactory.Config> {

    public static final String NAME = "Header";

    public HeaderRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        String headerName = config.getName();
        Pattern pattern = config.getRegex() != null ? Pattern.compile(config.getRegex()) : null;

        return exchange -> {
            String value = exchange.getRequest().requestHeaders().get(headerName);
            if (value == null) {
                return false;
            }
            if (pattern == null) {
                return true;
            }
            return pattern.matcher(value).matches();
        };
    }

    @Data
    public static class Config {
        private String name;
        private String regex;
    }
}
