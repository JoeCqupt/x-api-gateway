package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import lombok.Data;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class WeightRoutePredicateFactory
        extends AbstractConfigurable<WeightRoutePredicateFactory.Config>
        implements RoutePredicateFactory<WeightRoutePredicateFactory.Config> {

    public static final String NAME = "Weight";

    private static final ConcurrentHashMap<String, GroupState> GROUP_STATES = new ConcurrentHashMap<>();

    public WeightRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public RoutePredicate<ServerWebExchange> apply(Config config) {
        String group = config.getGroup();
        int weight = config.getWeight();

        GroupState state = GROUP_STATES.computeIfAbsent(group, k -> new GroupState());
        int routeIndex = state.registerRoute(weight);

        return exchange -> {
            long count = state.counter.getAndIncrement();
            int totalWeight = state.totalWeight;
            int mod = (int) (count % totalWeight);

            int cumulative = 0;
            for (int i = 0; i < state.weights.length; i++) {
                cumulative += state.weights[i];
                if (mod < cumulative) {
                    return i == routeIndex;
                }
            }
            return false;
        };
    }

    static void resetGroups() {
        GROUP_STATES.clear();
    }

    private static class GroupState {
        final AtomicLong counter = new AtomicLong(0);
        volatile int[] weights = new int[0];
        volatile int totalWeight = 0;
        private int routeCount = 0;

        synchronized int registerRoute(int weight) {
            int index = routeCount++;
            int[] newWeights = new int[routeCount];
            System.arraycopy(weights, 0, newWeights, 0, weights.length);
            newWeights[index] = weight;
            weights = newWeights;

            int total = 0;
            for (int w : newWeights) {
                total += w;
            }
            totalWeight = total;
            return index;
        }
    }

    @Data
    public static class Config {
        private String group;
        private int weight;
    }
}
