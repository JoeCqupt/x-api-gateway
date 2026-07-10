package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.concurrent.atomic.AtomicInteger;

public class ReactorHttpHandler {

    private final WebHandler webHandler;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private volatile boolean shuttingDown = false;

    public ReactorHttpHandler(WebHandler webHandler) {
        this.webHandler = webHandler;
    }

    public Publisher<Void> handle(HttpServerRequest request, HttpServerResponse response) {
        if (shuttingDown) {
            response.status(io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE);
            return response.sendString(Mono.just("{\"code\":503,\"message\":\"Server is shutting down\"}")).then();
        }
        activeRequests.incrementAndGet();
        ServerWebExchange exchange = new ServerWebExchange(request, response);
        return Mono.from(webHandler.handle(exchange))
                .doFinally(signal -> activeRequests.decrementAndGet());
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public void markShuttingDown() {
        this.shuttingDown = true;
    }

    public boolean awaitQuiescence(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (activeRequests.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return activeRequests.get() == 0;
    }
}
