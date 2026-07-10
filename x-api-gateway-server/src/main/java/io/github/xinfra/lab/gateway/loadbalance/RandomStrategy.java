package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class RandomStrategy implements LoadBalancerStrategy {

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

        int totalWeight = healthy.stream().mapToInt(UpstreamTarget::getWeight).sum();
        int random = ThreadLocalRandom.current().nextInt(totalWeight);

        int cursor = 0;
        for (UpstreamTarget target : healthy) {
            cursor += target.getWeight();
            if (random < cursor) {
                return target;
            }
        }
        return healthy.get(healthy.size() - 1);
    }
}
