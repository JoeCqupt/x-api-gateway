package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoveHeaderFilterTest {

    @Test
    public void testRemovesSingleHeader() {
        RemoveHeaderGatewayFilterFactory factory = new RemoveHeaderGatewayFilterFactory();
        RemoveHeaderGatewayFilterFactory.Config config = new RemoveHeaderGatewayFilterFactory.Config();
        config.setNames(Arrays.asList("X-Internal"));
        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Internal", "secret");
        headers.add("X-Keep", "visible");
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertNull(headers.get("X-Internal"));
        Assert.assertEquals("visible", headers.get("X-Keep"));
    }

    @Test
    public void testRemovesMultipleHeaders() {
        RemoveHeaderGatewayFilterFactory factory = new RemoveHeaderGatewayFilterFactory();
        RemoveHeaderGatewayFilterFactory.Config config = new RemoveHeaderGatewayFilterFactory.Config();
        config.setNames(Arrays.asList("X-Internal", "X-Debug"));
        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Internal", "secret");
        headers.add("X-Debug", "true");
        headers.add("Accept", "application/json");
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertNull(headers.get("X-Internal"));
        Assert.assertNull(headers.get("X-Debug"));
        Assert.assertEquals("application/json", headers.get("Accept"));
    }

    @Test
    public void testNullConfigNamesIsHandled() {
        RemoveHeaderGatewayFilterFactory factory = new RemoveHeaderGatewayFilterFactory();
        RemoveHeaderGatewayFilterFactory.Config config = new RemoveHeaderGatewayFilterFactory.Config();
        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Keep", "value");
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("value", headers.get("X-Keep"));
    }

    @Test
    public void testFactoryName() {
        Assert.assertEquals("RemoveHeader", new RemoveHeaderGatewayFilterFactory().getName());
    }
}
