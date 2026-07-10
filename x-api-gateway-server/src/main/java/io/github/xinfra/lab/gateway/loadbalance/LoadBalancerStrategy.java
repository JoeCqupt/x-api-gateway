package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;

import java.util.List;

public interface LoadBalancerStrategy {
    UpstreamTarget choose(List<UpstreamTarget> targets, ServerWebExchange exchange);
}
