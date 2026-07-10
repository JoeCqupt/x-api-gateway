package io.github.xinfra.lab.gateway.server;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

public class GatewayResponse {

    private final HttpServerResponse delegate;
    private volatile boolean committed = false;

    public GatewayResponse(HttpServerResponse delegate) {
        this.delegate = delegate;
    }

    public HttpResponseStatus status() {
        return delegate.status();
    }

    public GatewayResponse status(HttpResponseStatus status) {
        delegate.status(status);
        return this;
    }

    public HttpHeaders headers() {
        return delegate.responseHeaders();
    }

    public GatewayResponse header(String name, String value) {
        delegate.addHeader(name, value);
        return this;
    }

    public Mono<Void> writeWith(Mono<ByteBuf> body) {
        committed = true;
        return delegate.send(body).then();
    }

    public Mono<Void> setComplete() {
        committed = true;
        return delegate.send().then();
    }

    public boolean isCommitted() {
        return committed;
    }

    public HttpServerResponse unwrap() {
        return delegate;
    }
}
