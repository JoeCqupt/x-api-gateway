package io.github.xinfra.lab.gateway.endpoint;

import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import io.github.xinfra.lab.gateway.discovery.ServiceInstance;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.loadbalance.LoadBalancerStrategy;
import io.github.xinfra.lab.gateway.loadbalance.LoadBalancerStrategyFactory;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.netty.resources.ConnectionProvider;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.markResponseCommitted;

@Slf4j
public class WebSocketEndpoint implements Endpoint<WebSocketEndpoint.Config> {

    @Data
    public static class Config {
        List<String> urls;
        List<Integer> weights;
        String loadBalancer;
        String serviceId;
        int connectTimeout = 3000;
        int poolMaxConnections = 256;
    }

    private final Config config;
    private volatile List<UpstreamTarget> targets;
    private final LoadBalancerStrategy loadBalancerStrategy;
    private final HttpClient httpClient;

    public WebSocketEndpoint(Config config) {
        this(config, null);
    }

    public WebSocketEndpoint(Config config, ServiceDiscovery serviceDiscovery) {
        this.config = config;
        this.loadBalancerStrategy = LoadBalancerStrategyFactory.get(config.getLoadBalancer());
        ConnectionProvider provider = ConnectionProvider.builder("ws-upstream")
                .maxConnections(config.getPoolMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .build();
        this.httpClient = HttpClient.create(provider);

        if (config.getServiceId() != null && serviceDiscovery != null) {
            this.targets = buildTargetsFromDiscovery(serviceDiscovery, config.getServiceId());
            serviceDiscovery.subscribe(config.getServiceId(), instances -> {
                this.targets = instancesToTargets(instances);
                log.info("WebSocket service instances updated for {}: {} targets",
                        config.getServiceId(), this.targets.size());
            });
        } else {
            this.targets = buildTargets(config);
        }
    }

    private static List<UpstreamTarget> buildTargetsFromDiscovery(
            ServiceDiscovery discovery, String serviceId) {
        return instancesToTargets(discovery.getInstances(serviceId));
    }

    private static List<UpstreamTarget> instancesToTargets(List<ServiceInstance> instances) {
        List<UpstreamTarget> targets = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            if (instance.isHealthy()) {
                String url = "ws://" + instance.getHost() + ":" + instance.getPort();
                targets.add(new UpstreamTarget(url, 1));
            }
        }
        return targets;
    }

    private static List<UpstreamTarget> buildTargets(Config config) {
        List<UpstreamTarget> targets = new ArrayList<>();
        List<String> urls = config.getUrls();
        if (urls == null) {
            return targets;
        }
        List<Integer> weights = config.getWeights() != null ? config.getWeights() : Collections.emptyList();
        for (int i = 0; i < urls.size(); i++) {
            int weight = i < weights.size() ? weights.get(i) : 1;
            targets.add(new UpstreamTarget(urls.get(i), weight));
        }
        return targets;
    }

    @Override
    public Config getConfig() {
        return this.config;
    }

    public List<UpstreamTarget> getTargets() {
        return targets;
    }

    @Override
    public Mono<Void> invoke(ServerWebExchange exchange) {
        UpstreamTarget target = loadBalancerStrategy.choose(targets, exchange);
        if (target == null) {
            return Mono.error(new ErrorResponseException(
                    HttpResponseStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SERVICE_UNAVAILABLE));
        }

        target.getActiveConnections().incrementAndGet();

        URI targetUri = URI.create(target.getUrl());
        String wsScheme = "https".equals(targetUri.getScheme()) ? "wss" : "ws";
        String requestPath = exchange.getRequest().fullPath();
        String upstreamUrl = wsScheme + "://" + targetUri.getHost()
                + ":" + targetUri.getPort() + requestPath;

        return exchange.getResponse().sendWebsocket((serverIn, serverOut) ->
                httpClient.websocket()
                        .uri(upstreamUrl)
                        .handle((WebsocketInbound upstreamIn, WebsocketOutbound upstreamOut) -> {
                            Mono<Void> clientToUpstream = upstreamOut.send(serverIn.receive().retain()).then();
                            Mono<Void> upstreamToClient = serverOut.send(upstreamIn.receive().retain()).then();
                            return Mono.zip(clientToUpstream, upstreamToClient).then();
                        })
                        .then()
        ).then()
                .doOnTerminate(() -> {
                    target.getActiveConnections().decrementAndGet();
                    markResponseCommitted(exchange);
                })
                .doOnError(t -> log.error("WebSocket proxy error for target: {}", target.getUrl(), t));
    }

    public static boolean isWebSocketUpgrade(ServerWebExchange exchange) {
        String upgrade = exchange.getRequest().requestHeaders().get("Upgrade");
        return "websocket".equalsIgnoreCase(upgrade);
    }
}
