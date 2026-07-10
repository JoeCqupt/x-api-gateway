package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.ratelimit.LocalRateLimiter;
import io.github.xinfra.lab.gateway.ratelimit.RateLimiter;
import io.github.xinfra.lab.gateway.ratelimit.SlidingWindowRateLimiter;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Slf4j
@Order(-200)
public class RateLimitGatewayFilterFactory
        extends AbstractConfigurable<RateLimitGatewayFilterFactory.Config>
        implements GatewayFilterFactory<RateLimitGatewayFilterFactory.Config> {

    public static final String NAME = "RateLimit";

    public RateLimitGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        RateLimiter rateLimiter = createRateLimiter(config);
        AtomicReference<Config> configRef = new AtomicReference<>(config);

        GatewayFilter inner = (exchange, chain) -> {
            Config c = configRef.get();
            String key = resolveKey(exchange, c);
            return rateLimiter.isAllowed(key, c.getReplenishRate(), c.getBurstCapacity())
                    .flatMap(response -> {
                        if (!response.isAllowed()) {
                            DefaultHttpHeaders headers = new DefaultHttpHeaders();
                            headers.add("X-RateLimit-Limit", String.valueOf(c.getBurstCapacity()));
                            headers.add("X-RateLimit-Remaining", "0");
                            headers.add("Retry-After", String.valueOf(
                                    Math.max(1, response.getRetryAfterMs() / 1000)));
                            return Mono.error(new ErrorResponseException(
                                    HttpResponseStatus.TOO_MANY_REQUESTS,
                                    headers,
                                    ErrorCode.RATE_LIMITED));
                        }
                        return chain.filter(exchange);
                    });
        };

        return new ConfigurableGatewayFilter<>(configRef, inner, -200, NAME);
    }

    private RateLimiter createRateLimiter(Config config) {
        String type = config.getLimiterType();
        if ("redis".equalsIgnoreCase(type)) {
            try {
                Class<?> clazz = Class.forName(
                        "io.github.xinfra.lab.gateway.ratelimit.RedisRateLimiter");
                return (RateLimiter) clazz
                        .getConstructor(String.class)
                        .newInstance(config.getRedisUri());
            } catch (ClassNotFoundException e) {
                log.warn("RedisRateLimiter not available (lettuce not on classpath), falling back to local");
            } catch (Exception e) {
                log.error("Failed to create RedisRateLimiter, falling back to local", e);
            }
        }
        String algorithm = config.getAlgorithm();
        if ("slidingWindow".equalsIgnoreCase(algorithm)) {
            return new SlidingWindowRateLimiter();
        }
        return new LocalRateLimiter();
    }

    static String resolveKey(ServerWebExchange exchange, Config config) {
        String resolver = config.getKeyResolver();
        if (resolver == null) resolver = "remoteAddr";

        switch (resolver) {
            case "header":
                String headerName = config.getKeyHeader();
                if (headerName != null) {
                    String val = exchange.getRequest().requestHeaders().get(headerName);
                    if (val != null) return val;
                }
                return "unknown";
            case "routeId":
                Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                return route != null ? route.getId() : "unknown";
            case "remoteAddr":
            default:
                InetSocketAddress addr = exchange.getRequest().remoteAddress();
                return addr != null ? addr.getAddress().getHostAddress() : "unknown";
        }
    }

    @Data
    public static class Config {
        private long replenishRate = 100;
        private long burstCapacity = 200;
        private String keyResolver = "remoteAddr";
        private String keyHeader;
        private String limiterType = "local";
        private String algorithm = "tokenBucket";
        private String redisUri;
    }
}
