package io.github.xinfra.lab.gateway.filter.global;

import io.github.xinfra.lab.gateway.filter.GatewayFilterChain;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.net.InetSocketAddress;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.mockito.Mockito.*;

public class AccessLogTest {

    @Test
    public void testOrderIsMinus800() {
        AccessLogGatewayFilter filter = new AccessLogGatewayFilter();
        Assert.assertEquals(-800, filter.getOrder());
    }

    @Test
    public void testFilterCallsChainAndCompletes() {
        AccessLogGatewayFilter filter = new AccessLogGatewayFilter();

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.fullPath()).thenReturn("/api/test");
        when(request.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status()).thenReturn(HttpResponseStatus.OK);

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        java.util.concurrent.atomic.AtomicBoolean chainCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        Assert.assertTrue("Chain should be called", chainCalled.get());
    }

    @Test
    public void testStartTimeAttributeIsSet() {
        AccessLogGatewayFilter filter = new AccessLogGatewayFilter();

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.fullPath()).thenReturn("/test");
        when(request.remoteAddress()).thenReturn(new InetSocketAddress("10.0.0.1", 5000));

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status()).thenReturn(HttpResponseStatus.OK);

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        GatewayFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();

        Object startTime = exchange.getAttributes().get("gatewayStartTime");
        Assert.assertNotNull("Start time attribute should be set", startTime);
        Assert.assertTrue("Start time should be a Long", startTime instanceof Long);
    }

    @Test
    public void testImplementsGlobalGatewayFilter() {
        AccessLogGatewayFilter filter = new AccessLogGatewayFilter();
        Assert.assertTrue(filter instanceof GlobalGatewayFilter);
    }
}
