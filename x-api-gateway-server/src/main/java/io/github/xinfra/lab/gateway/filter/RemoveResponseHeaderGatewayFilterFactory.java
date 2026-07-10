package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.List;

@Order(510)
public class RemoveResponseHeaderGatewayFilterFactory
        extends AbstractConfigurable<RemoveResponseHeaderGatewayFilterFactory.Config>
        implements GatewayFilterFactory<RemoveResponseHeaderGatewayFilterFactory.Config> {

    public static final String NAME = "RemoveResponseHeader";

    public RemoveResponseHeaderGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) ->
                chain.filter(exchange).then(Mono.fromRunnable(() -> {
                    if (config != null && config.getHeaders() != null) {
                        for (String header : config.getHeaders()) {
                            exchange.getResponse().responseHeaders().remove(header);
                        }
                    }
                }));
    }

    @Data
    public static class Config {
        private List<String> headers;
    }
}
