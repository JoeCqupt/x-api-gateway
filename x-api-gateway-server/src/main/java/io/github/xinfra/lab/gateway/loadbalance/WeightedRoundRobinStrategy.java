package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;

import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;

/**
 * Nginx smooth weighted round-robin algorithm.
 *
 * Each round:
 *   1. currentWeight[i] += effectiveWeight[i]
 *   2. Pick the target with the highest currentWeight
 *   3. selected.currentWeight -= totalWeight
 */
public class WeightedRoundRobinStrategy implements LoadBalancerStrategy {

    private volatile AtomicIntegerArray currentWeights;
    private volatile int lastSize = -1;

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

        synchronized (this) {
            if (currentWeights == null || lastSize != healthy.size()) {
                currentWeights = new AtomicIntegerArray(healthy.size());
                lastSize = healthy.size();
            }

            int totalWeight = 0;
            for (UpstreamTarget t : healthy) {
                totalWeight += t.getWeight();
            }

            int maxIdx = 0;
            int maxWeight = Integer.MIN_VALUE;

            for (int i = 0; i < healthy.size(); i++) {
                int cw = currentWeights.addAndGet(i, healthy.get(i).getWeight());
                if (cw > maxWeight) {
                    maxWeight = cw;
                    maxIdx = i;
                }
            }

            currentWeights.addAndGet(maxIdx, -totalWeight);
            return healthy.get(maxIdx);
        }
    }
}
