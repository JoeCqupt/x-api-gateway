package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RewritePathFilterTest {

    private ServerWebExchange mockExchange(String path) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.fullPath()).thenReturn(path);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testSimpleRewrite() {
        RewritePathGatewayFilterFactory factory = new RewritePathGatewayFilterFactory();
        RewritePathGatewayFilterFactory.Config config = new RewritePathGatewayFilterFactory.Config();
        config.setRegexp("/api/v1/(.*)");
        config.setReplacement("/v2/$1");
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/api/v1/users");
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("/v2/users", exchange.getAttribute("gatewayRewrittenPath"));
        Assert.assertEquals("/api/v1/users", exchange.getAttribute("gatewayOriginalPath"));
    }

    @Test
    public void testStripPrefix() {
        RewritePathGatewayFilterFactory factory = new RewritePathGatewayFilterFactory();
        RewritePathGatewayFilterFactory.Config config = new RewritePathGatewayFilterFactory.Config();
        config.setRegexp("/service/(.*)");
        config.setReplacement("/$1");
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/service/orders/123");
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("/orders/123", exchange.getAttribute("gatewayRewrittenPath"));
    }

    @Test
    public void testNoMatch() {
        RewritePathGatewayFilterFactory factory = new RewritePathGatewayFilterFactory();
        RewritePathGatewayFilterFactory.Config config = new RewritePathGatewayFilterFactory.Config();
        config.setRegexp("/api/v1/(.*)");
        config.setReplacement("/v2/$1");
        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/other/path");
        filter.filter(exchange, ex -> Mono.empty()).block();

        Assert.assertEquals("/other/path", exchange.getAttribute("gatewayRewrittenPath"));
    }

    @Test
    public void testFactoryName() {
        Assert.assertEquals("RewritePath", new RewritePathGatewayFilterFactory().getName());
    }
}
