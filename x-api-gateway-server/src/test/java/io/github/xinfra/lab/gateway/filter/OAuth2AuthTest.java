package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;
import reactor.netty.http.server.HttpServerRequest;

public class OAuth2AuthTest {

    private DisposableServer introspectionServer;
    private int serverPort;

    @Before
    public void setup() {
        introspectionServer = HttpServer.create()
                .handle((req, resp) ->
                    req.receive().aggregate().asString().flatMap(body -> {
                        String json;
                        if (body.contains("token=valid-token")) {
                            json = "{\"active\":true,\"sub\":\"testuser\",\"scope\":\"read write\"}";
                        } else {
                            json = "{\"active\":false}";
                        }
                        return resp.sendString(Mono.just(json)).then();
                    })
                )
                .bindAddress(() -> new InetSocketAddress(0))
                .bind()
                .block();
        serverPort = introspectionServer.port();
    }

    @After
    public void teardown() {
        if (introspectionServer != null) {
            introspectionServer.disposeNow();
        }
    }

    private ServerWebExchange mockExchange(String authHeader) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        if (authHeader != null) {
            headers.add("Authorization", authHeader);
        }
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testOAuth2ValidToken() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("oauth2");
        config.setIntrospectionUri("http://localhost:" + serverPort + "/introspect");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setCacheTtlSeconds(0);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange("Bearer valid-token");

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        Assert.assertTrue(chainCalled.get());
        Assert.assertNotNull(exchange.getAttribute("gatewayAuthPrincipal"));
    }

    @Test
    public void testOAuth2InactiveToken() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("oauth2");
        config.setIntrospectionUri("http://localhost:" + serverPort + "/introspect");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setCacheTtlSeconds(0);

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange("Bearer expired-token");

        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
            Assert.assertEquals(401, ((ErrorResponseException) e.getCause()).getStatus().code());
        }
    }

    @Test
    public void testOAuth2MissingToken() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("oauth2");
        config.setIntrospectionUri("http://localhost:" + serverPort + "/introspect");
        config.setClientId("client");
        config.setClientSecret("secret");

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange(null);

        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
        }
    }

    @Test
    public void testOAuth2InvalidAuthHeader() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("oauth2");
        config.setIntrospectionUri("http://localhost:" + serverPort + "/introspect");
        config.setClientId("client");
        config.setClientSecret("secret");

        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = mockExchange("Basic credentials");

        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
        }
    }

    @Test
    public void testOAuth2CachedToken() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("oauth2");
        config.setIntrospectionUri("http://localhost:" + serverPort + "/introspect");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setCacheTtlSeconds(300);

        GatewayFilter filter = factory.apply(config);

        ServerWebExchange exchange1 = mockExchange("Bearer valid-token");
        AtomicBoolean chain1 = new AtomicBoolean(false);
        filter.filter(exchange1, ex -> {
            chain1.set(true);
            return Mono.empty();
        }).block();
        Assert.assertTrue(chain1.get());

        ServerWebExchange exchange2 = mockExchange("Bearer valid-token");
        AtomicBoolean chain2 = new AtomicBoolean(false);
        filter.filter(exchange2, ex -> {
            chain2.set(true);
            return Mono.empty();
        }).block();
        Assert.assertTrue(chain2.get());
        Assert.assertNotNull(exchange2.getAttribute("gatewayAuthPrincipal"));
    }
}
