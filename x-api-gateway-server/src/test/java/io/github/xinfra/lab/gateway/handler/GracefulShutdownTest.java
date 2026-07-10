package io.github.xinfra.lab.gateway.handler;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.NettyOutbound;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class GracefulShutdownTest {

    @Test
    public void testRejectsRequestsDuringShutdown() {
        WebHandler webHandler = exchange -> Mono.empty();
        ReactorHttpHandler handler = new ReactorHttpHandler(webHandler);

        handler.markShuttingDown();

        HttpServerRequest request = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.status(any(HttpResponseStatus.class))).thenReturn(response);
        NettyOutbound outbound = mock(NettyOutbound.class);
        when(outbound.then()).thenReturn(Mono.empty());
        when(response.sendString(any(Mono.class))).thenReturn(outbound);

        Mono.from(handler.handle(request, response)).block();

        verify(response).status(HttpResponseStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    public void testTracksActiveRequests() throws Exception {
        CountDownLatch filterLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);

        WebHandler webHandler = exchange -> {
            startedLatch.countDown();
            return Mono.fromRunnable(() -> {
                try {
                    filterLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        };

        ReactorHttpHandler handler = new ReactorHttpHandler(webHandler);

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        HttpServerResponse response = mock(HttpServerResponse.class);

        Assert.assertEquals(0, handler.getActiveRequests());

        Thread requestThread = new Thread(() -> {
            try {
                Mono.from(handler.handle(request, response)).block();
            } catch (Exception e) {
                // expected
            }
        });
        requestThread.start();

        startedLatch.await(5, TimeUnit.SECONDS);
        Assert.assertEquals(1, handler.getActiveRequests());

        filterLatch.countDown();
        requestThread.join(5000);
        Assert.assertEquals(0, handler.getActiveRequests());
    }

    @Test
    public void testAwaitQuiescenceTimesOut() throws Exception {
        CountDownLatch filterLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);

        WebHandler webHandler = exchange -> {
            startedLatch.countDown();
            return Mono.fromRunnable(() -> {
                try {
                    filterLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        };

        ReactorHttpHandler handler = new ReactorHttpHandler(webHandler);

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        HttpServerResponse response = mock(HttpServerResponse.class);

        Thread requestThread = new Thread(() -> {
            try {
                Mono.from(handler.handle(request, response)).block();
            } catch (Exception e) {
                // expected
            }
        });
        requestThread.start();

        startedLatch.await(5, TimeUnit.SECONDS);
        boolean quiesced = handler.awaitQuiescence(200);
        Assert.assertFalse(quiesced);

        filterLatch.countDown();
        requestThread.join(5000);
    }

    @Test
    public void testAwaitQuiescenceSucceedsWhenDrained() {
        WebHandler webHandler = exchange -> Mono.empty();
        ReactorHttpHandler handler = new ReactorHttpHandler(webHandler);
        Assert.assertTrue(handler.awaitQuiescence(100));
    }
}
