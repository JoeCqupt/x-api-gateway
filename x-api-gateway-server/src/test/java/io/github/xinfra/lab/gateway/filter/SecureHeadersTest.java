package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecureHeadersTest {

    @Test
    public void testSetsRealIpAndForwardedFor() throws Exception {
        SecureHeadersGatewayFilterFactory factory = new SecureHeadersGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        DefaultHttpHeaders requestHeaders = new DefaultHttpHeaders();
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(requestHeaders);
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        when(request.remoteAddress()).thenReturn(new InetSocketAddress(addr, 12345));

        DefaultHttpHeaders responseHeaders = new DefaultHttpHeaders();
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.responseHeaders()).thenReturn(responseHeaders);
        when(response.addHeader("X-Content-Type-Options", "nosniff")).thenReturn(response);
        when(response.addHeader("X-Frame-Options", "DENY")).thenReturn(response);
        when(response.addHeader("X-XSS-Protection", "1; mode=block")).thenReturn(response);

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        GatewayFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();

        Assert.assertEquals("10.0.0.1", requestHeaders.get("X-Real-IP"));
        Assert.assertEquals("10.0.0.1", requestHeaders.get("X-Forwarded-For"));
    }

    @Test
    public void testAppendsToExistingForwardedFor() throws Exception {
        SecureHeadersGatewayFilterFactory factory = new SecureHeadersGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        DefaultHttpHeaders requestHeaders = new DefaultHttpHeaders();
        requestHeaders.add("X-Forwarded-For", "1.2.3.4");
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(requestHeaders);
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        when(request.remoteAddress()).thenReturn(new InetSocketAddress(addr, 12345));

        DefaultHttpHeaders responseHeaders = new DefaultHttpHeaders();
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.responseHeaders()).thenReturn(responseHeaders);
        when(response.addHeader("X-Content-Type-Options", "nosniff")).thenReturn(response);
        when(response.addHeader("X-Frame-Options", "DENY")).thenReturn(response);
        when(response.addHeader("X-XSS-Protection", "1; mode=block")).thenReturn(response);

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        GatewayFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();

        Assert.assertEquals("1.2.3.4, 10.0.0.1", requestHeaders.get("X-Forwarded-For"));
    }

    @Test
    public void testRemovesInternalToken() throws Exception {
        SecureHeadersGatewayFilterFactory factory = new SecureHeadersGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);

        DefaultHttpHeaders requestHeaders = new DefaultHttpHeaders();
        requestHeaders.add("X-Internal-Token", "secret");
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(requestHeaders);
        when(request.remoteAddress()).thenReturn(null);

        DefaultHttpHeaders responseHeaders = new DefaultHttpHeaders();
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.responseHeaders()).thenReturn(responseHeaders);
        when(response.addHeader("X-Content-Type-Options", "nosniff")).thenReturn(response);
        when(response.addHeader("X-Frame-Options", "DENY")).thenReturn(response);
        when(response.addHeader("X-XSS-Protection", "1; mode=block")).thenReturn(response);

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        GatewayFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();

        Assert.assertNull(requestHeaders.get("X-Internal-Token"));
    }

    @Test
    public void testAddsSecurityResponseHeaders() throws Exception {
        SecureHeadersGatewayFilterFactory factory = new SecureHeadersGatewayFilterFactory();
        SecureHeadersGatewayFilterFactory.Config config = new SecureHeadersGatewayFilterFactory.Config();
        config.setSetForwardedHeaders(false);
        config.setAddSecurityHeaders(true);
        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);

        DefaultHttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add("Server", "netty");
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.responseHeaders()).thenReturn(responseHeaders);
        when(response.addHeader("X-Content-Type-Options", "nosniff")).thenReturn(response);
        when(response.addHeader("X-Frame-Options", "DENY")).thenReturn(response);
        when(response.addHeader("X-XSS-Protection", "1; mode=block")).thenReturn(response);

        ServerWebExchange exchange = new ServerWebExchange(request, response);

        GatewayFilterChain chain = ex -> Mono.empty();
        filter.filter(exchange, chain).block();

        Assert.assertNull(responseHeaders.get("Server"));
    }

    @Test
    public void testFactoryName() {
        SecureHeadersGatewayFilterFactory factory = new SecureHeadersGatewayFilterFactory();
        Assert.assertEquals("SecureHeaders", factory.getName());
    }
}
