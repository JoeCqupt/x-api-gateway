package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Order(-500)
public class AuthGatewayFilterFactory
        extends AbstractConfigurable<AuthGatewayFilterFactory.Config>
        implements GatewayFilterFactory<AuthGatewayFilterFactory.Config> {

    public static final String NAME = "Auth";
    private static final String AUTH_PRINCIPAL_ATTR = "gatewayAuthPrincipal";

    public AuthGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        String type = config.getType() != null ? config.getType() : "jwt";

        return (exchange, chain) -> {
            if ("apiKey".equalsIgnoreCase(type)) {
                return handleApiKey(exchange, config, chain);
            }
            if ("oauth2".equalsIgnoreCase(type)) {
                return handleOAuth2(exchange, config, chain);
            }
            if ("basic".equalsIgnoreCase(type)) {
                return handleBasicAuth(exchange, config, chain);
            }
            return handleJwt(exchange, config, chain);
        };
    }

    private Mono<Void> handleApiKey(io.github.xinfra.lab.gateway.server.ServerWebExchange exchange,
                                     Config config, GatewayFilterChain chain) {
        String headerName = config.getHeaderName() != null ? config.getHeaderName() : "X-Api-Key";
        String apiKey = exchange.getRequest().requestHeaders().get(headerName);

        if (apiKey == null || apiKey.isEmpty()) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        List<String> validKeys = config.getApiKeys() != null ? config.getApiKeys() : Collections.emptyList();
        if (!validKeys.contains(apiKey)) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        Map<String, Object> principal = new HashMap<>();
        principal.put("type", "apiKey");
        principal.put("key", apiKey);
        exchange.getAttributes().put(AUTH_PRINCIPAL_ATTR, principal);

        return chain.filter(exchange);
    }

    private Mono<Void> handleBasicAuth(io.github.xinfra.lab.gateway.server.ServerWebExchange exchange,
                                       Config config, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().requestHeaders().get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        int colonIdx = decoded.indexOf(':');
        if (colonIdx < 0) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        String username = decoded.substring(0, colonIdx);
        String password = decoded.substring(colonIdx + 1);

        Map<String, String> users = config.getUsers() != null ? config.getUsers() : Collections.emptyMap();
        String expectedPassword = users.get(username);
        if (expectedPassword == null || !java.security.MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                expectedPassword.getBytes(StandardCharsets.UTF_8))) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        Map<String, Object> principal = new HashMap<>();
        principal.put("type", "basic");
        principal.put("username", username);
        exchange.getAttributes().put(AUTH_PRINCIPAL_ATTR, principal);

        return chain.filter(exchange);
    }

    private Mono<Void> handleJwt(io.github.xinfra.lab.gateway.server.ServerWebExchange exchange,
                                  Config config, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().requestHeaders().get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        String token = authHeader.substring(7);
        try {
            Map<String, Object> claims = validateJwt(token, config.getSecretKey());
            exchange.getAttributes().put(AUTH_PRINCIPAL_ATTR, claims);
            return chain.filter(exchange);
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }
    }

    private Mono<Void> handleOAuth2(io.github.xinfra.lab.gateway.server.ServerWebExchange exchange,
                                     Config config, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().requestHeaders().get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
        }

        String token = authHeader.substring(7);

        CacheEntry cached = introspectionCache.get(token);
        if (cached != null && !cached.isExpired()) {
            exchange.getAttributes().put(AUTH_PRINCIPAL_ATTR, cached.claims);
            return chain.filter(exchange);
        }

        String credentials = config.getClientId() + ":" + config.getClientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        byte[] bodyBytes = ("token=" + token).getBytes(StandardCharsets.UTF_8);

        return HttpClient.create()
                .headers(h -> {
                    h.add("Authorization", "Basic " + basicAuth);
                    h.add("Content-Type", "application/x-www-form-urlencoded");
                })
                .post()
                .uri(config.getIntrospectionUri())
                .send(Mono.just(Unpooled.wrappedBuffer(bodyBytes)))
                .responseSingle((resp, body) -> body.asString())
                .flatMap(responseBody -> {
                    Map<String, Object> introspection = parseSimpleJson(responseBody);
                    Object active = introspection.get("active");
                    if (active == null || !Boolean.TRUE.equals(active)) {
                        return Mono.error(new ErrorResponseException(
                                HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
                    }

                    Map<String, Object> principal = new HashMap<>();
                    principal.put("type", "oauth2");
                    if (introspection.containsKey("sub")) {
                        principal.put("sub", introspection.get("sub"));
                    }
                    if (introspection.containsKey("scope")) {
                        principal.put("scope", introspection.get("scope"));
                    }

                    int ttl = config.getCacheTtlSeconds();
                    if (ttl > 0) {
                        introspectionCache.put(token,
                                new CacheEntry(principal, System.currentTimeMillis() + ttl * 1000L));
                    }

                    exchange.getAttributes().put(AUTH_PRINCIPAL_ATTR, principal);
                    return chain.filter(exchange);
                })
                .onErrorResume(ErrorResponseException.class, Mono::error)
                .onErrorResume(e -> {
                    log.error("OAuth2 introspection failed", e);
                    return Mono.error(new ErrorResponseException(
                            HttpResponseStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED));
                });
    }

    private static final ConcurrentHashMap<String, CacheEntry> introspectionCache =
            new ConcurrentHashMap<>();

    static class CacheEntry {
        final Map<String, Object> claims;
        final long expiresAtMs;

        CacheEntry(Map<String, Object> claims, long expiresAtMs) {
            this.claims = claims;
            this.expiresAtMs = expiresAtMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }

    static Map<String, Object> validateJwt(String token, String secretKey) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String headerPayload = parts[0] + "." + parts[1];
        byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        byte[] expectedSig = hmacSha256(headerPayload.getBytes(StandardCharsets.UTF_8),
                secretKey.getBytes(StandardCharsets.UTF_8));

        if (!constantTimeEquals(signatureBytes, expectedSig)) {
            throw new SecurityException("Invalid JWT signature");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> claims = parseSimpleJson(payloadJson);

        long now = System.currentTimeMillis() / 1000;
        if (claims.containsKey("exp")) {
            long exp = ((Number) claims.get("exp")).longValue();
            if (now > exp) {
                throw new SecurityException("JWT expired");
            }
        }
        if (claims.containsKey("nbf")) {
            long nbf = ((Number) claims.get("nbf")).longValue();
            if (now < nbf) {
                throw new SecurityException("JWT not yet valid");
            }
        }

        return claims;
    }

    static byte[] hmacSha256(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }

    static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    static Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        int i = 0;
        while (i < json.length()) {
            i = skipWhitespace(json, i);
            if (i >= json.length()) break;

            if (json.charAt(i) == '"') {
                int keyStart = i + 1;
                int keyEnd = json.indexOf('"', keyStart);
                String key = json.substring(keyStart, keyEnd);
                i = keyEnd + 1;
                i = skipWhitespace(json, i);
                if (i < json.length() && json.charAt(i) == ':') i++;
                i = skipWhitespace(json, i);

                if (i < json.length() && json.charAt(i) == '"') {
                    int valStart = i + 1;
                    int valEnd = json.indexOf('"', valStart);
                    map.put(key, json.substring(valStart, valEnd));
                    i = valEnd + 1;
                } else {
                    int valStart = i;
                    int valEnd = valStart;
                    while (valEnd < json.length() && json.charAt(valEnd) != ',' && json.charAt(valEnd) != '}') {
                        valEnd++;
                    }
                    String val = json.substring(valStart, valEnd).trim();
                    if (val.contains(".")) {
                        map.put(key, Double.parseDouble(val));
                    } else if ("true".equals(val) || "false".equals(val)) {
                        map.put(key, Boolean.parseBoolean(val));
                    } else {
                        map.put(key, Long.parseLong(val));
                    }
                    i = valEnd;
                }
            }
            if (i < json.length() && json.charAt(i) == ',') i++;
        }
        return map;
    }

    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    @Data
    public static class Config {
        private String type = "jwt";
        private String secretKey;
        private String headerName = "X-Api-Key";
        private List<String> apiKeys;
        private Map<String, String> users;
        private String introspectionUri;
        private String clientId;
        private String clientSecret;
        private int cacheTtlSeconds = 60;
    }
}
