package io.github.xinfra.lab.gateway.event;

import java.time.Instant;

public class RouteRefreshEvent {

    private final int routeCount;
    private final Instant timestamp;
    private final String source;

    public RouteRefreshEvent(int routeCount, String source) {
        this.routeCount = routeCount;
        this.timestamp = Instant.now();
        this.source = source;
    }

    public int getRouteCount() {
        return routeCount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }
}
