package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import java.net.ConnectException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

public class RetryFilterTest {

    private ServerWebExchange mockExchange(HttpMethod method) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        when(request.method()).thenReturn(method);
        when(request.fullPath()).thenReturn("/api/test");
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testRetryOnConnectException() {
        RetryGatewayFilterFactory factory = new RetryGatewayFilterFactory();
        RetryGatewayFilterFactory.Config config = new RetryGatewayFilterFactory.Config();
        config.setRetries(3);
        config.setFirstBackoffMs(10);
        config.setMaxBackoffMs(50);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange(HttpMethod.GET);

        AtomicInteger attempts = new AtomicInteger(0);
        GatewayFilterChain chain = ex -> {
            int attempt = attempts.incrementAndGet();
            if (attempt <= 1) {
                return Mono.error(new ConnectException("Connection refused"));
            }
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        Assert.assertEquals(2, attempts.get());
    }

    @Test
    public void testNoRetryForPostMethod() {
        RetryGatewayFilterFactory factory = new RetryGatewayFilterFactory();
        RetryGatewayFilterFactory.Config config = new RetryGatewayFilterFactory.Config();
        config.setRetries(3);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange(HttpMethod.POST);

        AtomicInteger attempts = new AtomicInteger(0);
        GatewayFilterChain chain = ex -> {
            attempts.incrementAndGet();
            return Mono.error(new ConnectException("Connection refused"));
        };

        try {
            filter.filter(exchange, chain).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            // POST should not be retried
            Assert.assertEquals(1, attempts.get());
        }
    }

    @Test
    public void testRetryOnRetryableStatus() {
        Set<Integer> retryableStatuses = new HashSet<>(Arrays.asList(502, 503));

        ErrorResponseException ere = new ErrorResponseException(
                HttpResponseStatus.BAD_GATEWAY, ErrorCode.BAD_GATEWAY);
        Assert.assertTrue(RetryGatewayFilterFactory.isRetryable(ere, retryableStatuses));

        ErrorResponseException nonRetryable = new ErrorResponseException(
                HttpResponseStatus.NOT_FOUND, ErrorCode.ROUTE_NO_MATCH);
        Assert.assertFalse(RetryGatewayFilterFactory.isRetryable(nonRetryable, retryableStatuses));
    }

    @Test
    public void testRetryOnConnectExceptionIsRetryable() {
        Set<Integer> statuses = new HashSet<>();
        Assert.assertTrue(RetryGatewayFilterFactory.isRetryable(
                new ConnectException("refused"), statuses));
    }

    @Test
    public void testNonRetryableExceptionNotRetried() {
        Set<Integer> statuses = new HashSet<>(Arrays.asList(502));
        Assert.assertFalse(RetryGatewayFilterFactory.isRetryable(
                new RuntimeException("unknown"), statuses));
    }
}
