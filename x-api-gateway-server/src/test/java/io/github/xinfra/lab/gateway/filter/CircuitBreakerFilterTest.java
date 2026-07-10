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

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.mockito.Mockito.*;

public class CircuitBreakerFilterTest {

    private ServerWebExchange mockExchange(String routeId) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        when(request.method()).thenReturn(HttpMethod.GET);
        ServerWebExchange exchange = new ServerWebExchange(request, null);
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(routeId);
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    @Test
    public void testClosedStateAllowsCalls() {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(10);
        config.setFailureRateThreshold(50.0);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        Assert.assertTrue(cb.isCallPermitted());
        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    public void testTransitionToOpenOnHighFailureRate() {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(10);
        config.setFailureRateThreshold(50.0);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        // 6 failures + 4 successes = 60% failure rate > 50% threshold
        for (int i = 0; i < 6; i++) cb.recordFailure();
        for (int i = 0; i < 4; i++) cb.recordSuccess();

        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    public void testOpenStateRejectsCalls() {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(10);
        config.setFailureRateThreshold(50.0);
        config.setWaitDurationInOpenStateMs(60000);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        for (int i = 0; i < 10; i++) cb.recordFailure();

        Assert.assertFalse(cb.isCallPermitted());
    }

    @Test
    public void testHalfOpenAfterWaitDuration() throws InterruptedException {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(5);
        config.setFailureRateThreshold(50.0);
        config.setWaitDurationInOpenStateMs(100);
        config.setHalfOpenPermits(2);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        for (int i = 0; i < 5; i++) cb.recordFailure();
        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.OPEN, cb.getState());

        Thread.sleep(150);
        Assert.assertTrue(cb.isCallPermitted());
        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.HALF_OPEN, cb.getState());
    }

    @Test
    public void testHalfOpenSuccessTransitionsToClosed() throws InterruptedException {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(5);
        config.setFailureRateThreshold(50.0);
        config.setWaitDurationInOpenStateMs(100);
        config.setHalfOpenPermits(2);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        for (int i = 0; i < 5; i++) cb.recordFailure();
        Thread.sleep(150);
        cb.isCallPermitted(); // triggers transition to HALF_OPEN

        cb.recordSuccess();
        cb.recordSuccess();

        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    public void testHalfOpenFailureTransitionsToOpen() throws InterruptedException {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(5);
        config.setFailureRateThreshold(50.0);
        config.setWaitDurationInOpenStateMs(100);
        config.setHalfOpenPermits(2);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        for (int i = 0; i < 5; i++) cb.recordFailure();
        Thread.sleep(150);
        cb.isCallPermitted();

        cb.recordFailure();
        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    public void testSlidingWindowDoesNotTripBelowThreshold() {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(10);
        config.setFailureRateThreshold(50.0);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        // 4 failures + 6 successes = 40% failure rate < 50% threshold
        for (int i = 0; i < 4; i++) cb.recordFailure();
        for (int i = 0; i < 6; i++) cb.recordSuccess();

        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.CLOSED, cb.getState());
        Assert.assertTrue(cb.isCallPermitted());
    }

    @Test
    public void testSlidingWindowOverwritesOldEntries() {
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(10);
        config.setFailureRateThreshold(50.0);

        CircuitBreakerGatewayFilterFactory.CircuitBreaker cb =
                new CircuitBreakerGatewayFilterFactory.CircuitBreaker(config);

        // First 10 calls: 4 failures + 6 successes (40%, stays closed)
        for (int i = 0; i < 4; i++) cb.recordFailure();
        for (int i = 0; i < 6; i++) cb.recordSuccess();
        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.CLOSED, cb.getState());

        // Next 10 calls: all successes (overwrite old entries)
        for (int i = 0; i < 10; i++) cb.recordSuccess();
        Assert.assertEquals(CircuitBreakerGatewayFilterFactory.CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    public void testFilterRejectsWhenOpen() {
        CircuitBreakerGatewayFilterFactory factory = new CircuitBreakerGatewayFilterFactory();
        CircuitBreakerGatewayFilterFactory.Config config = new CircuitBreakerGatewayFilterFactory.Config();
        config.setSlidingWindowSize(5);
        config.setFailureRateThreshold(50.0);
        config.setWaitDurationInOpenStateMs(60000);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange("test-route");

        GatewayFilterChain failChain = ex -> Mono.error(new RuntimeException("upstream error"));

        // Trigger enough failures to open circuit
        for (int i = 0; i < 5; i++) {
            try {
                filter.filter(exchange, failChain).block();
            } catch (Exception ignored) {}
        }

        // Next call should be rejected by circuit breaker
        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
            Assert.assertEquals(503, ((ErrorResponseException) e.getCause()).getStatus().code());
        }
    }
}
