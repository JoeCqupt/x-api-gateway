package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Order(-900)
public class RequestIdGatewayFilterFactory
        extends AbstractConfigurable<RequestIdGatewayFilterFactory.Config>
        implements GatewayFilterFactory<RequestIdGatewayFilterFactory.Config> {

    public static final String NAME = "RequestId";
    public static final String DEFAULT_HEADER_NAME = "X-Request-Id";

    public RequestIdGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        String headerName = config != null && config.getHeaderName() != null
                ? config.getHeaderName() : DEFAULT_HEADER_NAME;

        return (exchange, chain) -> {
            String existing = exchange.getRequest().requestHeaders().get(headerName);
            String requestId = existing != null ? existing : UUID.randomUUID().toString();

            exchange.getRequest().requestHeaders().set(headerName, requestId);
            exchange.getAttributes().put("gatewayRequestId", requestId);

            return chain.filter(exchange).then(Mono.fromRunnable(() ->
                    exchange.getResponse().addHeader(headerName, requestId)));
        };
    }

    @Data
    public static class Config {
        private String headerName = DEFAULT_HEADER_NAME;
    }
}
