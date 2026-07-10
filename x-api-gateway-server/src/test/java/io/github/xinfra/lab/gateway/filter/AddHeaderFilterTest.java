package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AddHeaderFilterTest {

    @Test
    public void testAddsHeaders() {
        AddHeaderGatewayFilterFactory factory = new AddHeaderGatewayFilterFactory();
        Map<String, String> config = new HashMap<>();
        config.put("X-Gateway", "x-api-gateway");
        config.put("X-Env", "test");
        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("x-api-gateway", headers.get("X-Gateway"));
        Assert.assertEquals("test", headers.get("X-Env"));
    }

    @Test
    public void testNullConfigDoesNothing() {
        AddHeaderGatewayFilterFactory factory = new AddHeaderGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertTrue(headers.isEmpty());
    }

    @Test
    public void testPreservesExistingHeaders() {
        AddHeaderGatewayFilterFactory factory = new AddHeaderGatewayFilterFactory();
        Map<String, String> config = new HashMap<>();
        config.put("X-New", "new-value");
        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Existing", "existing-value");
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("existing-value", headers.get("X-Existing"));
        Assert.assertEquals("new-value", headers.get("X-New"));
    }

    @Test
    public void testFactoryName() {
        Assert.assertEquals("AddHeader", new AddHeaderGatewayFilterFactory().getName());
    }
}
