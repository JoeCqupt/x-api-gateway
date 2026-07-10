package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StripPrefixFilterTest {

    private ServerWebExchange mockExchange(String path) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.fullPath()).thenReturn(path);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testStripOneSegment() {
        StripPrefixGatewayFilterFactory factory = new StripPrefixGatewayFilterFactory();
        StripPrefixGatewayFilterFactory.Config config = new StripPrefixGatewayFilterFactory.Config();
        config.setParts(1);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/api/users/123");
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("/users/123", exchange.getAttribute("gatewayRewrittenPath"));
        Assert.assertEquals("/api/users/123", exchange.getAttribute("gatewayOriginalPath"));
    }

    @Test
    public void testStripTwoSegments() {
        StripPrefixGatewayFilterFactory factory = new StripPrefixGatewayFilterFactory();
        StripPrefixGatewayFilterFactory.Config config = new StripPrefixGatewayFilterFactory.Config();
        config.setParts(2);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/api/v1/orders/456");
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("/orders/456", exchange.getAttribute("gatewayRewrittenPath"));
    }

    @Test
    public void testStripAllSegmentsReturnsRoot() {
        StripPrefixGatewayFilterFactory factory = new StripPrefixGatewayFilterFactory();
        StripPrefixGatewayFilterFactory.Config config = new StripPrefixGatewayFilterFactory.Config();
        config.setParts(3);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/a/b/c");
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("/", exchange.getAttribute("gatewayRewrittenPath"));
    }

    @Test
    public void testStripWithRootPath() {
        StripPrefixGatewayFilterFactory factory = new StripPrefixGatewayFilterFactory();
        StripPrefixGatewayFilterFactory.Config config = new StripPrefixGatewayFilterFactory.Config();
        config.setParts(1);
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/");
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("/", exchange.getAttribute("gatewayRewrittenPath"));
    }

    @Test
    public void testDefaultConfigParts() {
        StripPrefixGatewayFilterFactory.Config config = new StripPrefixGatewayFilterFactory.Config();
        Assert.assertEquals(1, config.getParts());
    }

    @Test
    public void testFactoryName() {
        Assert.assertEquals("StripPrefix", new StripPrefixGatewayFilterFactory().getName());
    }
}
