package io.github.xinfra.lab.gateway.loadbalance;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class UpstreamTarget {
    private final String url;
    private final int weight;
    private volatile boolean healthy = true;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    public UpstreamTarget(String url, int weight) {
        this.url = url;
        this.weight = weight > 0 ? weight : 1;
    }

    public UpstreamTarget(String url) {
        this(url, 1);
    }
}
