package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class AddResponseHeaderFilterTest {

    @Test
    public void testAddsResponseHeaders() {
        AddResponseHeaderGatewayFilterFactory factory = new AddResponseHeaderGatewayFilterFactory();
        Map<String, String> config = new HashMap<>();
        config.put("X-Response-Time", "0ms");
        config.put("X-Gateway", "x-api-gateway");
        GatewayFilter filter = factory.apply(config);

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.addHeader(anyString(), anyString())).thenReturn(response);

        ServerWebExchange exchange = new ServerWebExchange(null, response);

        filter.filter(exchange, ex -> Mono.empty()).block();

        verify(response).addHeader("X-Response-Time", "0ms");
        verify(response).addHeader("X-Gateway", "x-api-gateway");
    }

    @Test
    public void testNullConfigDoesNothing() {
        AddResponseHeaderGatewayFilterFactory factory = new AddResponseHeaderGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        HttpServerResponse response = mock(HttpServerResponse.class);
        ServerWebExchange exchange = new ServerWebExchange(null, response);

        filter.filter(exchange, ex -> Mono.empty()).block();

        verify(response, times(0)).addHeader(anyString(), anyString());
    }

    @Test
    public void testFactoryName() {
        Assert.assertEquals("AddResponseHeader", new AddResponseHeaderGatewayFilterFactory().getName());
    }
}
