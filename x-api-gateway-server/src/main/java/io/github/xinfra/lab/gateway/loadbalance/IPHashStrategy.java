package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

public class IPHashStrategy implements LoadBalancerStrategy {

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

        String clientIp = "unknown";
        if (exchange != null) {
            InetSocketAddress remoteAddress = exchange.getRequest().remoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                clientIp = remoteAddress.getAddress().getHostAddress();
            }
        }

        int hash = Math.abs(clientIp.hashCode());
        int index = hash % healthy.size();
        return healthy.get(index);
    }
}
