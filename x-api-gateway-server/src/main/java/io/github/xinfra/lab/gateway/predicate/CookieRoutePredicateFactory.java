package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.util.regex.Pattern;

public class CookieRoutePredicateFactory
        extends AbstractConfigurable<CookieRoutePredicateFactory.Config>
        implements RoutePredicateFactory<CookieRoutePredicateFactory.Config> {

    public static final String NAME = "Cookie";

    public CookieRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        String cookieName = config.getName();
        Pattern valuePattern = config.getRegex() != null ? Pattern.compile(config.getRegex()) : null;

        return exchange -> {
            String cookieHeader = exchange.getRequest().requestHeaders().get("Cookie");
            if (cookieHeader == null || cookieHeader.isEmpty()) {
                return false;
            }
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String trimmed = cookie.trim();
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    String name = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    if (cookieName.equals(name)) {
                        if (valuePattern == null) {
                            return true;
                        }
                        return valuePattern.matcher(value).matches();
                    }
                }
            }
            return false;
        };
    }

    @Data
    public static class Config {
        private String name;
        private String regex;
    }
}
