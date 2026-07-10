package io.github.xinfra.lab.gateway.handler;

import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;

public class ReactorHttpHandlerTest {

    @Test
    public void testActiveRequestTracking() {
        WebHandler webHandler = exchange -> Mono.empty();
        ReactorHttpHandler handler = new ReactorHttpHandler(webHandler);

        Assert.assertEquals(0, handler.getActiveRequests());
    }

    @Test
    public void testMarkShuttingDown() {
        WebHandler webHandler = exchange -> Mono.empty();
        ReactorHttpHandler handler = new ReactorHttpHandler(webHandler);

        handler.markShuttingDown();
        Assert.assertTrue(handler.awaitQuiescence(100));
    }

    @Test
    public void testAwaitQuiescenceNoRequests() {
        WebHandler webHandler = exchange -> Mono.empty();
        ReactorHttpHandler handler = new ReactorHttpHandler(webHandler);

        Assert.assertTrue(handler.awaitQuiescence(100));
    }
}
