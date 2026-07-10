package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ConsistentHashStrategy implements LoadBalancerStrategy {

    private static final int DEFAULT_VIRTUAL_NODES = 160;
    private static final String DEFAULT_HASH_KEY_HEADER = null;

    private final int virtualNodes;
    private final String hashKeyHeader;

    private volatile TreeMap<Long, UpstreamTarget> ring;
    private volatile int lastHashCode;

    public ConsistentHashStrategy() {
        this(DEFAULT_VIRTUAL_NODES, DEFAULT_HASH_KEY_HEADER);
    }

    public ConsistentHashStrategy(int virtualNodes, String hashKeyHeader) {
        this.virtualNodes = virtualNodes;
        this.hashKeyHeader = hashKeyHeader;
    }

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

        int currentHashCode = computeTargetListHash(healthy);
        if (ring == null || currentHashCode != lastHashCode) {
            synchronized (this) {
                if (ring == null || currentHashCode != lastHashCode) {
                    ring = buildRing(healthy);
                    lastHashCode = currentHashCode;
                }
            }
        }

        String key = extractKey(exchange);
        long hash = hash(key);

        SortedMap<Long, UpstreamTarget> tail = ring.tailMap(hash);
        if (tail.isEmpty()) {
            return ring.firstEntry().getValue();
        }
        return tail.get(tail.firstKey());
    }

    private TreeMap<Long, UpstreamTarget> buildRing(List<UpstreamTarget> targets) {
        TreeMap<Long, UpstreamTarget> newRing = new TreeMap<>();
        for (UpstreamTarget target : targets) {
            for (int i = 0; i < virtualNodes; i++) {
                String virtualKey = target.getUrl() + "#" + i;
                long h = hash(virtualKey);
                newRing.put(h, target);
            }
        }
        return newRing;
    }

    private String extractKey(ServerWebExchange exchange) {
        if (hashKeyHeader != null) {
            String headerValue = exchange.getRequest().requestHeaders().get(hashKeyHeader);
            if (headerValue != null) {
                return headerValue;
            }
        }
        InetSocketAddress remoteAddress = exchange.getRequest().remoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private int computeTargetListHash(List<UpstreamTarget> targets) {
        int result = targets.size();
        for (UpstreamTarget t : targets) {
            result = 31 * result + t.getUrl().hashCode();
        }
        return result;
    }

    static long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((long) (digest[3] & 0xFF) << 24)
                    | ((long) (digest[2] & 0xFF) << 16)
                    | ((long) (digest[1] & 0xFF) << 8)
                    | ((long) (digest[0] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            return key.hashCode() & 0xFFFFFFFFL;
        }
    }
}
