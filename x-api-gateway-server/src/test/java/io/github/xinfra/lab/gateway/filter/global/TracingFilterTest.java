package io.github.xinfra.lab.gateway.filter.global;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import static org.mockito.Mockito.*;

public class TracingFilterTest {

    @Test
    public void testIsAvailable() {
        Assert.assertTrue(TracingFilter.isAvailable());
    }

    @Test
    public void testTracingFilterCreation() {
        TracingFilter filter = new TracingFilter("test-service");
        Assert.assertNotNull(filter);
    }

    @Test
    public void testTracingFilterSetsSpanAttribute() {
        TracingFilter filter = new TracingFilter("test-service");

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.fullPath()).thenReturn("/api/test");
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertNotNull(exchange.getAttribute(TracingFilter.TRACING_SPAN_ATTR));
    }

    @Test
    public void testTracingFilterWithError() {
        TracingFilter filter = new TracingFilter("test-service");

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.POST);
        when(request.fullPath()).thenReturn("/api/error");
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        try {
            filter.filter(exchange, ex ->
                    Mono.error(new RuntimeException("test error"))).block();
            Assert.fail("Should throw");
        } catch (RuntimeException e) {
            Assert.assertEquals("test error", e.getMessage());
        }

        Assert.assertNotNull(exchange.getAttribute(TracingFilter.TRACING_SPAN_ATTR));
    }
}
