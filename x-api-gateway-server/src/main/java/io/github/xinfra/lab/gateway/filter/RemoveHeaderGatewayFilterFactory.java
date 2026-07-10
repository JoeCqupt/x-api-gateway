package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;

import java.util.List;

@Order(-20)
public class RemoveHeaderGatewayFilterFactory
        extends AbstractConfigurable<RemoveHeaderGatewayFilterFactory.Config>
        implements GatewayFilterFactory<RemoveHeaderGatewayFilterFactory.Config> {

    public static final String NAME = "RemoveHeader";

    public RemoveHeaderGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (config.getNames() != null) {
                config.getNames().forEach(name ->
                        exchange.getRequest().requestHeaders().remove(name));
            }
            return chain.filter(exchange);
        };
    }

    @Data
    public static class Config {
        private List<String> names;
    }
}
