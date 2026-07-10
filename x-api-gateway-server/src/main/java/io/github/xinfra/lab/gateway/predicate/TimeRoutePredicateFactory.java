package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.time.ZonedDateTime;

public class TimeRoutePredicateFactory
        extends AbstractConfigurable<TimeRoutePredicateFactory.Config>
        implements RoutePredicateFactory<TimeRoutePredicateFactory.Config> {

    public static final String NAME = "Time";

    public TimeRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        ZonedDateTime afterTime = config.getAfter() != null ? ZonedDateTime.parse(config.getAfter()) : null;
        ZonedDateTime beforeTime = config.getBefore() != null ? ZonedDateTime.parse(config.getBefore()) : null;

        return exchange -> {
            ZonedDateTime now = ZonedDateTime.now();
            if (afterTime != null && now.isBefore(afterTime)) {
                return false;
            }
            if (beforeTime != null && now.isAfter(beforeTime)) {
                return false;
            }
            return true;
        };
    }

    @Data
    public static class Config {
        private String after;
        private String before;
    }
}
