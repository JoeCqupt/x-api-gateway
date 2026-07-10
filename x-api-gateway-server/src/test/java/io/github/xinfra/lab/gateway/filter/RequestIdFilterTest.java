package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestIdFilterTest {

    private ServerWebExchange mockExchange() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.addHeader("X-Request-Id", "")).thenReturn(response);

        return new ServerWebExchange(request, response);
    }

    @Test
    public void testGeneratesRequestIdWhenMissing() {
        RequestIdGatewayFilterFactory factory = new RequestIdGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        ServerWebExchange exchange = mockExchange();
        HttpServerResponse response = exchange.getResponse();
        when(response.addHeader(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(response);

        filter.filter(exchange, ex -> Mono.empty()).block();

        String requestId = exchange.getAttribute("gatewayRequestId");
        Assert.assertNotNull(requestId);
        Assert.assertFalse(requestId.isEmpty());
        Assert.assertEquals(requestId, exchange.getRequest().requestHeaders().get("X-Request-Id"));
    }

    @Test
    public void testPreservesExistingRequestId() {
        RequestIdGatewayFilterFactory factory = new RequestIdGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        ServerWebExchange exchange = mockExchange();
        exchange.getRequest().requestHeaders().set("X-Request-Id", "existing-id");
        HttpServerResponse response = exchange.getResponse();
        when(response.addHeader(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(response);

        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("existing-id", exchange.getAttribute("gatewayRequestId"));
    }

    @Test
    public void testCustomHeaderName() {
        RequestIdGatewayFilterFactory factory = new RequestIdGatewayFilterFactory();
        RequestIdGatewayFilterFactory.Config config = new RequestIdGatewayFilterFactory.Config();
        config.setHeaderName("X-Trace-Id");
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange();
        HttpServerResponse response = exchange.getResponse();
        when(response.addHeader(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(response);

        filter.filter(exchange, ex -> Mono.empty()).block();

        String traceId = exchange.getRequest().requestHeaders().get("X-Trace-Id");
        Assert.assertNotNull(traceId);
    }

    @Test
    public void testFactoryName() {
        Assert.assertEquals("RequestId", new RequestIdGatewayFilterFactory().getName());
    }
}
