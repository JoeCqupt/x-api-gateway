package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import reactor.core.publisher.Mono;

@Order(-10)
public class RequestSizeGatewayFilterFactory
        extends AbstractConfigurable<RequestSizeGatewayFilterFactory.Config>
        implements GatewayFilterFactory<RequestSizeGatewayFilterFactory.Config> {

    public static final String NAME = "RequestSize";

    public RequestSizeGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        long maxSize = config.getMaxSize();
        return (exchange, chain) -> {
            String contentLengthStr = exchange.getRequest().requestHeaders().get("Content-Length");
            if (contentLengthStr != null) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    if (contentLength > maxSize) {
                        return Mono.error(new ErrorResponseException(
                                HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                                ErrorCode.REQUEST_TOO_LARGE));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return chain.filter(exchange);
        };
    }

    @Data
    public static class Config {
        private long maxSize = 10 * 1024 * 1024; // 10MB default
    }
}
