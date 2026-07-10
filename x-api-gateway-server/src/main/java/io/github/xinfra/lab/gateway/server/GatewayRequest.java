package io.github.xinfra.lab.gateway.server;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import reactor.core.publisher.Flux;
import reactor.netty.http.server.HttpServerRequest;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GatewayRequest {

    private final HttpServerRequest delegate;

    public GatewayRequest(HttpServerRequest delegate) {
        this.delegate = delegate;
    }

    public String path() {
        return delegate.path();
    }

    public String fullPath() {
        return delegate.fullPath();
    }

    public String uri() {
        return delegate.uri();
    }

    public HttpMethod method() {
        return delegate.method();
    }

    public HttpHeaders headers() {
        return delegate.requestHeaders();
    }

    public InetSocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    public Map<String, List<String>> queryParams() {
        String uri = delegate.uri();
        if (uri == null) {
            return Collections.emptyMap();
        }
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        return decoder.parameters();
    }

    public Map<String, String> cookies() {
        String cookieHeader = delegate.requestHeaders().get("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Cookie> decoded = ServerCookieDecoder.STRICT.decode(cookieHeader);
        Map<String, String> result = new LinkedHashMap<>();
        for (Cookie cookie : decoded) {
            result.put(cookie.name(), cookie.value());
        }
        return result;
    }

    public Flux<ByteBuf> body() {
        return delegate.receive().retain();
    }

    public HttpServerRequest unwrap() {
        return delegate;
    }
}
