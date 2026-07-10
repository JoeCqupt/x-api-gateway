package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.Map;

@Order(500)
public class AddResponseHeaderGatewayFilterFactory
        extends AbstractConfigurable<Map<String, String>>
        implements GatewayFilterFactory<Map<String, String>> {

    public static final String NAME = "AddResponseHeader";

    public AddResponseHeaderGatewayFilterFactory() {
        super((Class) Map.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Map<String, String> config) {
        return (exchange, chain) ->
                chain.filter(exchange).then(Mono.fromRunnable(() -> {
                    if (config != null) {
                        config.forEach((k, v) ->
                                exchange.getResponse().addHeader(k, v));
                    }
                }));
    }
}
