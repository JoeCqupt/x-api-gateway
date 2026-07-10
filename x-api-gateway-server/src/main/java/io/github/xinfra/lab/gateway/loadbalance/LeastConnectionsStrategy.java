package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class LeastConnectionsStrategy implements LoadBalancerStrategy {

    @Override
    public UpstreamTarget choose(List<UpstreamTarget> targets, ServerWebExchange exchange) {
        List<UpstreamTarget> healthy = targets.stream()
                .filter(UpstreamTarget::isHealthy)
                .collect(Collectors.toList());
        if (healthy.isEmpty()) {
            return null;
        }
        if (healthy.size() == 1) {
            return healthy.get(0);
        }

        int minConnections = Integer.MAX_VALUE;
        for (UpstreamTarget target : healthy) {
            int conns = target.getActiveConnections().get();
            if (conns < minConnections) {
                minConnections = conns;
            }
        }

        List<UpstreamTarget> candidates = new ArrayList<>();
        for (UpstreamTarget target : healthy) {
            if (target.getActiveConnections().get() == minConnections) {
                candidates.add(target);
            }
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
