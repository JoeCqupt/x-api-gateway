package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;

@Order(-50)
public class StripPrefixGatewayFilterFactory
        extends AbstractConfigurable<StripPrefixGatewayFilterFactory.Config>
        implements GatewayFilterFactory<StripPrefixGatewayFilterFactory.Config> {

    public static final String NAME = "StripPrefix";

    public StripPrefixGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        int parts = config.getParts();
        return (exchange, chain) -> {
            String path = exchange.getRequest().fullPath();
            String newPath = stripPrefix(path, parts);
            exchange.getAttributes().put("gatewayOriginalPath", path);
            exchange.getAttributes().put("gatewayRewrittenPath", newPath);
            return chain.filter(exchange);
        };
    }

    private String stripPrefix(String path, int parts) {
        String[] segments = path.split("/");
        StringBuilder newPath = new StringBuilder();
        int stripped = 0;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].isEmpty()) {
                continue;
            }
            if (stripped < parts) {
                stripped++;
                continue;
            }
            newPath.append("/").append(segments[i]);
        }
        if (newPath.length() == 0) {
            return "/";
        }
        return newPath.toString();
    }

    @Data
    public static class Config {
        private int parts = 1;
    }
}
