package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.endpoint.Endpoint;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.filter.GatewayFilterChain;
import io.github.xinfra.lab.gateway.filter.global.GlobalGatewayFilter;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.route.DefaultRoute;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.mockito.Mockito.*;

public class HandlerChainTest {

    private ServerWebExchange mockExchange() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.fullPath()).thenReturn("/api/test");
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        when(request.remoteAddress()).thenReturn(null);

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status()).thenReturn(HttpResponseStatus.OK);
        when(response.addHeader(anyString(), anyString())).thenReturn(response);
        when(response.status(any(HttpResponseStatus.class))).thenReturn(response);
        when(response.sendByteArray(any())).thenReturn(mock(reactor.netty.NettyOutbound.class));

        return new ServerWebExchange(request, response);
    }

    private DefaultRoute createRoute(String id) {
        DefaultRoute route = new DefaultRoute();
        route.setId(id);
        route.setFilters(Collections.<GatewayFilter>emptyList());
        route.setEndpoint(mock(Endpoint.class));
        return route;
    }

    // --- FilteringWebHandler tests ---

    @Test
    public void testFilteringWebHandlerMergesFilters() {
        AtomicInteger callOrder = new AtomicInteger(0);
        List<Integer> executionOrder = new ArrayList<>();

        GlobalGatewayFilter globalFilter = new GlobalGatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                executionOrder.add(callOrder.incrementAndGet());
                return chain.filter(exchange);
            }
        };

        GatewayFilter defaultFilter = new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                executionOrder.add(callOrder.incrementAndGet());
                return chain.filter(exchange);
            }
        };

        FilteringWebHandler handler = new FilteringWebHandler(
                Collections.singletonList(globalFilter),
                Collections.singletonList(defaultFilter));

        ServerWebExchange exchange = mockExchange();
        DefaultRoute route = createRoute("test");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

        handler.handle(exchange).block();

        Assert.assertEquals(2, executionOrder.size());
    }

    @Test
    public void testFilteringWebHandlerIncludesRouteFilters() {
        AtomicInteger filterCount = new AtomicInteger(0);

        GatewayFilter routeFilter = (exchange, chain) -> {
            filterCount.incrementAndGet();
            return chain.filter(exchange);
        };

        FilteringWebHandler handler = new FilteringWebHandler(
                Collections.<GlobalGatewayFilter>emptyList(),
                Collections.<GatewayFilter>emptyList());

        ServerWebExchange exchange = mockExchange();
        DefaultRoute route = createRoute("test");
        route.setFilters(Collections.singletonList(routeFilter));
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

        handler.handle(exchange).block();

        Assert.assertEquals(1, filterCount.get());
    }

    // --- MetricsWebHandler tests ---

    @Test
    public void testMetricsWebHandlerRecordsMetrics() {
        MetricsCollector collector = MetricsCollector.INSTANCE;
        collector.reset();

        WebHandler delegate = exchange -> Mono.empty();
        MetricsWebHandler handler = new MetricsWebHandler(delegate, collector);

        ServerWebExchange exchange = mockExchange();
        DefaultRoute route = createRoute("metrics-test");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

        handler.handle(exchange).block();

        String prometheus = collector.exportPrometheus();
        Assert.assertTrue(prometheus.contains("x_gateway_requests_total"));
    }

    @Test
    public void testMetricsWebHandlerSetsStartTime() {
        AtomicBoolean hasStartTime = new AtomicBoolean(false);
        WebHandler delegate = exchange -> {
            hasStartTime.set(exchange.getAttribute("gatewayStartTime") != null);
            return Mono.empty();
        };

        MetricsWebHandler handler = new MetricsWebHandler(delegate, MetricsCollector.INSTANCE);
        ServerWebExchange exchange = mockExchange();

        handler.handle(exchange).block();

        Assert.assertTrue(hasStartTime.get());
    }

    // --- ExceptionHandlingWebHandler tests ---

    @Test
    public void testExceptionHandlerHandlesError() {
        WebHandler delegate = exchange ->
                Mono.error(new ErrorResponseException(HttpResponseStatus.NOT_FOUND, ErrorCode.ROUTE_NO_MATCH));

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        WebExceptionHandler exceptionHandler = (exchange, ex) -> {
            handlerCalled.set(true);
            return Mono.empty();
        };

        ExceptionHandlingWebHandler handler = new ExceptionHandlingWebHandler(
                delegate, Collections.singletonList(exceptionHandler));

        ServerWebExchange exchange = mockExchange();
        handler.handle(exchange).block();

        Assert.assertTrue(handlerCalled.get());
    }

    @Test
    public void testExceptionHandlerCatchesSyncException() {
        WebHandler delegate = exchange -> {
            throw new RuntimeException("sync error");
        };

        AtomicReference<Throwable> captured = new AtomicReference<>();
        WebExceptionHandler exceptionHandler = (exchange, ex) -> {
            captured.set(ex);
            return Mono.empty();
        };

        ExceptionHandlingWebHandler handler = new ExceptionHandlingWebHandler(
                delegate, Collections.singletonList(exceptionHandler));

        ServerWebExchange exchange = mockExchange();
        handler.handle(exchange).block();

        Assert.assertNotNull(captured.get());
        Assert.assertEquals("sync error", captured.get().getMessage());
    }

    @Test
    public void testExceptionHandlerStoresHandledException() {
        WebHandler delegate = exchange ->
                Mono.error(new RuntimeException("test error"));

        WebExceptionHandler exceptionHandler = (exchange, ex) -> Mono.empty();

        ExceptionHandlingWebHandler handler = new ExceptionHandlingWebHandler(
                delegate, Collections.singletonList(exceptionHandler));

        ServerWebExchange exchange = mockExchange();
        handler.handle(exchange).block();

        Object stored = exchange.getAttribute(ExceptionHandlingWebHandler.HANDLED_WEB_EXCEPTION);
        Assert.assertNotNull(stored);
    }

    // --- AccessLogWebHandler tests ---

    @Test
    public void testAccessLogWebHandlerDelegates() {
        AtomicBoolean delegateCalled = new AtomicBoolean(false);
        WebHandler delegate = exchange -> {
            delegateCalled.set(true);
            return Mono.empty();
        };

        AccessLogWebHandler handler = new AccessLogWebHandler(delegate);
        ServerWebExchange exchange = mockExchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, createRoute("log-test"));

        handler.handle(exchange).block();

        Assert.assertTrue(delegateCalled.get());
    }

    @Test
    public void testAccessLogWebHandlerSetsStartTime() {
        AtomicBoolean hasStartTime = new AtomicBoolean(false);
        WebHandler delegate = exchange -> {
            hasStartTime.set(exchange.getAttribute("gatewayAccessLogStartTime") != null);
            return Mono.empty();
        };

        AccessLogWebHandler handler = new AccessLogWebHandler(delegate);
        ServerWebExchange exchange = mockExchange();

        handler.handle(exchange).block();

        Assert.assertTrue(hasStartTime.get());
    }

    // --- DefaultWebExceptionHandler tests ---

    @Test
    public void testDefaultWebExceptionHandlerMapsErrorResponse() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status(any(HttpResponseStatus.class))).thenReturn(response);
        when(response.addHeader(anyString(), anyString())).thenReturn(response);
        reactor.netty.NettyOutbound outbound = mock(reactor.netty.NettyOutbound.class);
        when(outbound.then()).thenReturn(Mono.empty());
        when(response.send(any(Mono.class))).thenReturn(outbound);

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.path()).thenReturn("/test");
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        ErrorResponseException ex = new ErrorResponseException(
                HttpResponseStatus.NOT_FOUND, ErrorCode.ROUTE_NO_MATCH);

        handler.handle(exchange, ex).block();

        verify(response).status(HttpResponseStatus.NOT_FOUND);
    }

    @Test
    public void testDefaultWebExceptionHandlerMapsUnknownException() {
        DefaultWebExceptionHandler handler = new DefaultWebExceptionHandler();

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status(any(HttpResponseStatus.class))).thenReturn(response);
        when(response.addHeader(anyString(), anyString())).thenReturn(response);
        reactor.netty.NettyOutbound outbound = mock(reactor.netty.NettyOutbound.class);
        when(outbound.then()).thenReturn(Mono.empty());
        when(response.send(any(Mono.class))).thenReturn(outbound);

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.path()).thenReturn("/test");
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        handler.handle(exchange, new RuntimeException("unexpected")).block();

        verify(response).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
}
