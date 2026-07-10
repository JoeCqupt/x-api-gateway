package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

public class AuthFilterTest {

    private static String createTestJwt(String subject, long exp, String secret) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + subject + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        String sigInput = header + "." + payload;
        byte[] sig = AuthGatewayFilterFactory.hmacSha256(
                sigInput.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        return header + "." + payload + "." + signature;
    }

    @Test
    public void testValidJwt() {
        String secret = "my-test-secret-key-1234567890";
        long expiry = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestJwt("testuser", expiry, secret);

        Map<String, Object> claims = AuthGatewayFilterFactory.validateJwt(token, secret);
        Assert.assertEquals("testuser", claims.get("sub"));
    }

    @Test(expected = SecurityException.class)
    public void testInvalidSignature() {
        String secret = "my-test-secret-key-1234567890";
        long expiry = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestJwt("testuser", expiry, secret);

        AuthGatewayFilterFactory.validateJwt(token, "wrong-secret");
    }

    @Test(expected = SecurityException.class)
    public void testExpiredJwt() {
        String secret = "my-test-secret-key-1234567890";
        long expiredTime = System.currentTimeMillis() / 1000 - 3600;
        String token = createTestJwt("testuser", expiredTime, secret);

        AuthGatewayFilterFactory.validateJwt(token, secret);
    }

    @Test
    public void testJwtFilterPassesWithValidToken() {
        String secret = "my-test-secret-key-1234567890";
        long expiry = System.currentTimeMillis() / 1000 + 3600;
        String token = createTestJwt("testuser", expiry, secret);

        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("jwt");
        config.setSecretKey(secret);

        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        Assert.assertTrue(chainCalled.get());
        Assert.assertNotNull(exchange.getAttribute("gatewayAuthPrincipal"));
    }

    @Test
    public void testJwtFilterRejectsWithoutToken() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("jwt");
        config.setSecretKey("secret");

        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());

        ServerWebExchange exchange = new ServerWebExchange(request, null);

        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
            Assert.assertEquals(401, ((ErrorResponseException) e.getCause()).getStatus().code());
        }
    }

    @Test
    public void testApiKeyValidKey() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("apiKey");
        config.setHeaderName("X-Api-Key");
        config.setApiKeys(Arrays.asList("key-1", "key-2"));

        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Api-Key", "key-1");
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        Assert.assertTrue(chainCalled.get());
    }

    @Test
    public void testApiKeyInvalidKey() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("apiKey");
        config.setApiKeys(Arrays.asList("valid-key"));

        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Api-Key", "wrong-key");
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);

        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
            Assert.assertEquals(401, ((ErrorResponseException) e.getCause()).getStatus().code());
        }
    }

    @Test
    public void testBasicAuthValidCredentials() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("basic");
        java.util.Map<String, String> users = new java.util.HashMap<>();
        users.put("admin", "password123");
        config.setUsers(users);

        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:password123".getBytes(StandardCharsets.UTF_8));
        headers.add("Authorization", "Basic " + credentials);
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        Assert.assertTrue(chainCalled.get());
        Map principal = exchange.getAttribute("gatewayAuthPrincipal");
        Assert.assertNotNull(principal);
        Assert.assertEquals("basic", principal.get("type"));
        Assert.assertEquals("admin", principal.get("username"));
    }

    @Test
    public void testBasicAuthInvalidPassword() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("basic");
        java.util.Map<String, String> users = new java.util.HashMap<>();
        users.put("admin", "password123");
        config.setUsers(users);

        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:wrongpass".getBytes(StandardCharsets.UTF_8));
        headers.add("Authorization", "Basic " + credentials);
        when(request.requestHeaders()).thenReturn(headers);

        ServerWebExchange exchange = new ServerWebExchange(request, null);

        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
            Assert.assertEquals(401, ((ErrorResponseException) e.getCause()).getStatus().code());
        }
    }

    @Test
    public void testBasicAuthMissingHeader() {
        AuthGatewayFilterFactory factory = new AuthGatewayFilterFactory();
        AuthGatewayFilterFactory.Config config = new AuthGatewayFilterFactory.Config();
        config.setType("basic");

        GatewayFilter filter = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());

        ServerWebExchange exchange = new ServerWebExchange(request, null);

        try {
            filter.filter(exchange, ex -> Mono.empty()).block();
            Assert.fail("Should throw");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof ErrorResponseException);
        }
    }

    @Test
    public void testSimpleJsonParsing() {
        Map<String, Object> result = AuthGatewayFilterFactory.parseSimpleJson(
                "{\"sub\":\"admin\",\"exp\":1234567890,\"admin\":true}");
        Assert.assertEquals("admin", result.get("sub"));
        Assert.assertEquals(1234567890L, result.get("exp"));
        Assert.assertEquals(true, result.get("admin"));
    }
}
