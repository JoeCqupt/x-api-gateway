package io.github.xinfra.lab.gateway.event;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class EventBusTest {

    @Test
    public void testPublishRouteRefreshEvent() {
        GatewayEventBus bus = new GatewayEventBus();
        AtomicInteger count = new AtomicInteger(0);
        AtomicReference<RouteRefreshEvent> received = new AtomicReference<>();

        bus.onRouteRefresh(event -> {
            count.incrementAndGet();
            received.set(event);
        });

        bus.publishRouteRefresh(new RouteRefreshEvent(5, "test"));

        Assert.assertEquals(1, count.get());
        Assert.assertNotNull(received.get());
        Assert.assertEquals(5, received.get().getRouteCount());
        Assert.assertEquals("test", received.get().getSource());
        Assert.assertNotNull(received.get().getTimestamp());
    }

    @Test
    public void testRemoveListener() {
        GatewayEventBus bus = new GatewayEventBus();
        AtomicInteger count = new AtomicInteger(0);
        Consumer<RouteRefreshEvent> listener = event -> count.incrementAndGet();

        bus.onRouteRefresh(listener);
        bus.publishRouteRefresh(new RouteRefreshEvent(1, "test"));
        Assert.assertEquals(1, count.get());

        bus.removeRouteRefreshListener(listener);
        bus.publishRouteRefresh(new RouteRefreshEvent(2, "test"));
        Assert.assertEquals(1, count.get());
    }

    @Test
    public void testListenerExceptionDoesNotStopOthers() {
        GatewayEventBus bus = new GatewayEventBus();
        AtomicInteger count = new AtomicInteger(0);

        bus.onRouteRefresh(event -> {
            throw new RuntimeException("boom");
        });
        bus.onRouteRefresh(event -> count.incrementAndGet());

        bus.publishRouteRefresh(new RouteRefreshEvent(1, "test"));
        Assert.assertEquals(1, count.get());
    }

    @Test
    public void testMultipleListeners() {
        GatewayEventBus bus = new GatewayEventBus();
        AtomicInteger total = new AtomicInteger(0);

        bus.onRouteRefresh(event -> total.addAndGet(event.getRouteCount()));
        bus.onRouteRefresh(event -> total.addAndGet(event.getRouteCount()));

        bus.publishRouteRefresh(new RouteRefreshEvent(3, "test"));
        Assert.assertEquals(6, total.get());
    }
}
