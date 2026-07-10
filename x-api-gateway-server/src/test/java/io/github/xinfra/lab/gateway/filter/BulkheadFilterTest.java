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

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.mockito.Mockito.*;

public class BulkheadFilterTest {

    private ServerWebExchange mockExchange(String routeId) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        ServerWebExchange exchange = new ServerWebExchange(request, null);
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(routeId);
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    @Test
    public void testAllowedWithinLimit() {
        BulkheadGatewayFilterFactory factory = new BulkheadGatewayFilterFactory();
        BulkheadGatewayFilterFactory.Config config = new BulkheadGatewayFilterFactory.Config();
        config.setMaxConcurrentCalls(5);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange("test-route");

        AtomicInteger chainCalled = new AtomicInteger(0);
        GatewayFilterChain chain = ex -> {
            chainCalled.incrementAndGet();
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        Assert.assertEquals(1, chainCalled.get());
    }

    @Test
    public void testRejectedOverLimit() {
        BulkheadGatewayFilterFactory factory = new BulkheadGatewayFilterFactory();
        BulkheadGatewayFilterFactory.Config config = new BulkheadGatewayFilterFactory.Config();
        config.setMaxConcurrentCalls(1);
        config.setMaxWaitMs(0);

        GatewayFilter filter = factory.apply(config);

        CountDownLatch holdLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);

        GatewayFilterChain blockingChain = ex -> {
            startedLatch.countDown();
            try {
                holdLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Mono.empty();
        };

        Thread t = new Thread(() -> {
            filter.filter(mockExchange("block-route"), blockingChain).block();
        });
        t.start();

        try {
            startedLatch.await(5, TimeUnit.SECONDS);
            Thread.sleep(50);

            filter.filter(mockExchange("block-route"), ex -> Mono.empty()).block();
            Assert.fail("Should have thrown ErrorResponseException");
        } catch (Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof ErrorResponseException) break;
                cause = cause.getCause();
            }
            Assert.assertNotNull("Expected ErrorResponseException in chain", cause);
        } finally {
            holdLatch.countDown();
            try { t.join(5000); } catch (InterruptedException ignored) {}
        }
    }

    @Test
    public void testSemaphoreReleasedAfterCompletion() {
        BulkheadGatewayFilterFactory factory = new BulkheadGatewayFilterFactory();
        BulkheadGatewayFilterFactory.Config config = new BulkheadGatewayFilterFactory.Config();
        config.setMaxConcurrentCalls(1);

        GatewayFilter filter = factory.apply(config);
        GatewayFilterChain chain = ex -> Mono.empty();

        for (int i = 0; i < 10; i++) {
            filter.filter(mockExchange("release-route"), chain).block();
        }
    }

    @Test
    public void testDefaultConfig() {
        BulkheadGatewayFilterFactory.Config config = new BulkheadGatewayFilterFactory.Config();
        Assert.assertEquals(25, config.getMaxConcurrentCalls());
        Assert.assertEquals(0, config.getMaxWaitMs());
    }

    @Test
    public void testFactoryName() {
        BulkheadGatewayFilterFactory factory = new BulkheadGatewayFilterFactory();
        Assert.assertEquals("Bulkhead", factory.getName());
    }
}
