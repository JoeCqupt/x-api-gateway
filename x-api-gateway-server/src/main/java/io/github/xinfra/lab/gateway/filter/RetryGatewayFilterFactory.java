package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR;

@Slf4j
@Order(150)
public class RetryGatewayFilterFactory
        extends AbstractConfigurable<RetryGatewayFilterFactory.Config>
        implements GatewayFilterFactory<RetryGatewayFilterFactory.Config> {

    public static final String NAME = "Retry";

    public RetryGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        Set<String> retryableMethods = new HashSet<>(config.getMethods());
        Set<Integer> retryableStatuses = new HashSet<>(config.getStatuses());

        return (exchange, chain) -> {
            String method = exchange.getRequest().method().name();
            if (!retryableMethods.contains(method)) {
                return chain.filter(exchange);
            }

            return Mono.defer(() -> chain.filter(exchange))
                    .retryWhen(Retry.backoff(config.getRetries(),
                                    Duration.ofMillis(config.getFirstBackoffMs()))
                            .maxBackoff(Duration.ofMillis(config.getMaxBackoffMs()))
                            .jitter(config.getJitterFactor())
                            .filter(throwable -> isRetryable(throwable, retryableStatuses))
                            .doBeforeRetry(signal -> {
                                log.warn("Retry attempt {} for request {} {}",
                                        signal.totalRetries() + 1,
                                        exchange.getRequest().method(),
                                        exchange.getRequest().fullPath());
                                exchange.getAttributes().put(GATEWAY_ALREADY_ROUTED_ATTR, false);
                            })
                    );
        };
    }

    static boolean isRetryable(Throwable t, Set<Integer> retryableStatuses) {
        if (t instanceof ConnectException || t instanceof TimeoutException) {
            return true;
        }
        if (t instanceof ErrorResponseException) {
            ErrorResponseException ere = (ErrorResponseException) t;
            return retryableStatuses.contains(ere.getStatus().code());
        }
        Throwable cause = t.getCause();
        if (cause instanceof ConnectException || cause instanceof TimeoutException) {
            return true;
        }
        return false;
    }

    @Data
    public static class Config {
        private int retries = 3;
        private List<Integer> statuses = Arrays.asList(502, 503);
        private List<String> methods = Arrays.asList("GET");
        private long firstBackoffMs = 50;
        private long maxBackoffMs = 500;
        private double jitterFactor = 0.5;
    }
}
