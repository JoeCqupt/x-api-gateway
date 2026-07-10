package io.github.xinfra.lab.gateway.loadbalance;

import java.util.HashMap;
import java.util.Map;

public class LoadBalancerStrategyFactory {

    public static final String WEIGHTED_ROUND_ROBIN = "WeightedRoundRobin";
    public static final String RANDOM = "Random";
    public static final String LEAST_CONNECTIONS = "LeastConnections";
    public static final String CONSISTENT_HASH = "ConsistentHash";
    public static final String IP_HASH = "IPHash";

    private static final Map<String, LoadBalancerStrategy> STRATEGIES = new HashMap<>();

    static {
        STRATEGIES.put(WEIGHTED_ROUND_ROBIN, new WeightedRoundRobinStrategy());
        STRATEGIES.put(RANDOM, new RandomStrategy());
        STRATEGIES.put(LEAST_CONNECTIONS, new LeastConnectionsStrategy());
        STRATEGIES.put(CONSISTENT_HASH, new ConsistentHashStrategy());
        STRATEGIES.put(IP_HASH, new IPHashStrategy());
    }

    public static LoadBalancerStrategy get(String name) {
        if (name == null) {
            return STRATEGIES.get(WEIGHTED_ROUND_ROBIN);
        }
        LoadBalancerStrategy strategy = STRATEGIES.get(name);
        if (strategy == null) {
            return STRATEGIES.get(WEIGHTED_ROUND_ROBIN);
        }
        return strategy;
    }
}
