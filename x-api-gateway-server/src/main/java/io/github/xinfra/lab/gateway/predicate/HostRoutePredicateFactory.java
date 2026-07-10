package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HostRoutePredicateFactory
        extends AbstractConfigurable<HostRoutePredicateFactory.Config>
        implements RoutePredicateFactory<HostRoutePredicateFactory.Config> {

    public static final String NAME = "Host";

    public HostRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        List<Pattern> patterns = config.getPatterns().stream()
                .map(HostRoutePredicateFactory::toRegexPattern)
                .collect(Collectors.toList());

        return exchange -> {
            String host = exchange.getRequest().requestHeaders().get("Host");
            if (host == null) {
                return false;
            }
            // strip port if present
            int portIdx = host.indexOf(':');
            if (portIdx > 0) {
                host = host.substring(0, portIdx);
            }
            for (Pattern pattern : patterns) {
                if (pattern.matcher(host).matches()) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Pattern toRegexPattern(String hostPattern) {
        String regex = hostPattern
                .replace(".", "\\.")
                .replace("**", "<<DOUBLESTAR>>")
                .replace("*", "[^.]+")
                .replace("<<DOUBLESTAR>>", ".+");
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    @Data
    public static class Config {
        private List<String> patterns;
    }
}
