package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.mockito.Mockito.*;

public class RateLimitFilterTest {

    private ServerWebExchange mockExchange(String ip) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.remoteAddress()).thenReturn(new InetSocketAddress(ip, 12345));
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testAllowedWithinBurst() {
        RateLimitGatewayFilterFactory factory = new RateLimitGatewayFilterFactory();
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setReplenishRate(10);
        config.setBurstCapacity(5);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange("10.0.0.1");

        AtomicInteger chainCalled = new AtomicInteger(0);
        GatewayFilterChain chain = ex -> {
            chainCalled.incrementAndGet();
            return Mono.empty();
        };

        for (int i = 0; i < 5; i++) {
            filter.filter(exchange, chain).block();
        }
        Assert.assertEquals(5, chainCalled.get());
    }

    @Test
    public void testRejectedAfterBurstExhausted() {
        RateLimitGatewayFilterFactory factory = new RateLimitGatewayFilterFactory();
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setReplenishRate(1);
        config.setBurstCapacity(2);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange("10.0.0.2");
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();
        filter.filter(exchange, chain).block();

        try {
            filter.filter(exchange, chain).block();
            Assert.fail("Should have thrown ErrorResponseException");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
            ErrorResponseException ere = (ErrorResponseException) e.getCause();
            Assert.assertEquals(429, ere.getStatus().code());
        }
    }

    @Test
    public void testKeyResolverRemoteAddr() {
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setKeyResolver("remoteAddr");

        ServerWebExchange exchange = mockExchange("192.168.1.1");
        String key = RateLimitGatewayFilterFactory.resolveKey(exchange, config);
        Assert.assertEquals("192.168.1.1", key);
    }

    @Test
    public void testKeyResolverHeader() {
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setKeyResolver("header");
        config.setKeyHeader("X-Api-Key");

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Api-Key", "my-key-123");
        when(request.requestHeaders()).thenReturn(headers);
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        String key = RateLimitGatewayFilterFactory.resolveKey(exchange, config);
        Assert.assertEquals("my-key-123", key);
    }

    @Test
    public void testKeyResolverRouteId() {
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setKeyResolver("routeId");

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        Route route = mock(Route.class);
        when(route.getId()).thenReturn("user-service");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

        String key = RateLimitGatewayFilterFactory.resolveKey(exchange, config);
        Assert.assertEquals("user-service", key);
    }

    @Test
    public void testDifferentKeysHaveSeparateBuckets() {
        RateLimitGatewayFilterFactory factory = new RateLimitGatewayFilterFactory();
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setReplenishRate(1);
        config.setBurstCapacity(1);

        GatewayFilter filter = factory.apply(config);
        GatewayFilterChain chain = ex -> Mono.empty();

        ServerWebExchange exchange1 = mockExchange("10.0.0.1");
        ServerWebExchange exchange2 = mockExchange("10.0.0.2");

        filter.filter(exchange1, chain).block();
        filter.filter(exchange2, chain).block();
        // Both should succeed — separate buckets
    }
}
