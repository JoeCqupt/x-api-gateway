package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.util.Map;
import java.util.regex.Pattern;

public class QueryRoutePredicateFactory
        extends AbstractConfigurable<QueryRoutePredicateFactory.Config>
        implements RoutePredicateFactory<QueryRoutePredicateFactory.Config> {

    public static final String NAME = "Query";

    public QueryRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        String paramName = config.getParam();
        Pattern pattern = config.getRegex() != null ? Pattern.compile(config.getRegex()) : null;

        return exchange -> {
            Map<String, String> params = exchange.getRequest().params();
            if (params == null) {
                return false;
            }
            String value = params.get(paramName);
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
        private String param;
        private String regex;
    }
}
