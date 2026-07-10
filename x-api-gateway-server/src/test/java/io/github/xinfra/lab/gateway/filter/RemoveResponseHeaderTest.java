package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoveResponseHeaderTest {

    @Test
    public void testRemovesSpecifiedHeaders() {
        RemoveResponseHeaderGatewayFilterFactory factory = new RemoveResponseHeaderGatewayFilterFactory();
        RemoveResponseHeaderGatewayFilterFactory.Config config = new RemoveResponseHeaderGatewayFilterFactory.Config();
        config.setHeaders(Arrays.asList("Server", "X-Powered-By"));

        GatewayFilter filter = factory.apply(config);

        DefaultHttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add("Server", "nginx");
        responseHeaders.add("X-Powered-By", "Express");
        responseHeaders.add("Content-Type", "application/json");

        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.responseHeaders()).thenReturn(responseHeaders);

        HttpServerRequest request = mock(HttpServerRequest.class);
        ServerWebExchange exchange = new ServerWebExchange(request, response);

        GatewayFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();

        Assert.assertNull(responseHeaders.get("Server"));
        Assert.assertNull(responseHeaders.get("X-Powered-By"));
        Assert.assertEquals("application/json", responseHeaders.get("Content-Type"));
    }

    @Test
    public void testNullConfig() {
        RemoveResponseHeaderGatewayFilterFactory factory = new RemoveResponseHeaderGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        HttpServerResponse response = mock(HttpServerResponse.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        ServerWebExchange exchange = new ServerWebExchange(request, response);

        GatewayFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();
    }

    @Test
    public void testFactoryName() {
        RemoveResponseHeaderGatewayFilterFactory factory = new RemoveResponseHeaderGatewayFilterFactory();
        Assert.assertEquals("RemoveResponseHeader", factory.getName());
    }
}
