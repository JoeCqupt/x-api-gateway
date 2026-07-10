package io.github.xinfra.lab.gateway.endpoint;

import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.healthcheck.HealthChecker;
import io.github.xinfra.lab.gateway.loadbalance.LoadBalancerStrategy;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.loadbalance.LoadBalancerStrategyFactory;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import io.github.xinfra.lab.gateway.discovery.ServiceInstance;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.markResponseCommitted;

@Slf4j
public class HttpEndpoint implements Endpoint<HttpEndpoint.Config> {

    @Data
    public static class Config {
        List<String> urls;
        List<Integer> weights;
        String loadBalancer;
        String serviceId;
        int connectTimeout = 3000;
        int readTimeout = 10000;
        int poolMaxConnections = 256;
        int poolMaxIdleTime = 60000;
        int poolMaxLifeTime = 300000;
        int poolEvictionInterval = 30000;
    }

    private final Config config;
    private volatile List<UpstreamTarget> targets;
    private final LoadBalancerStrategy loadBalancerStrategy;
    private final HttpClient httpClient;

    public HttpEndpoint(Config config) {
        this(config, null);
    }

    public HttpEndpoint(Config config, ServiceDiscovery serviceDiscovery) {
        this.config = config;
        this.loadBalancerStrategy = LoadBalancerStrategyFactory.get(config.getLoadBalancer());
        this.httpClient = buildHttpClient(config);

        if (config.getServiceId() != null && serviceDiscovery != null) {
            this.targets = buildTargetsFromDiscovery(serviceDiscovery, config.getServiceId());
            serviceDiscovery.subscribe(config.getServiceId(), instances -> {
                this.targets = instancesToTargets(instances);
                log.info("Service instances updated for {}: {} targets",
                        config.getServiceId(), this.targets.size());
            });
        } else {
            this.targets = buildTargets(config);
        }
    }

    private static List<UpstreamTarget> buildTargetsFromDiscovery(
            ServiceDiscovery discovery, String serviceId) {
        List<ServiceInstance> instances = discovery.getInstances(serviceId);
        return instancesToTargets(instances);
    }

    private static List<UpstreamTarget> instancesToTargets(List<ServiceInstance> instances) {
        List<UpstreamTarget> targets = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            if (instance.isHealthy()) {
                String url = "http://" + instance.getHost() + ":" + instance.getPort();
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

    private static HttpClient buildHttpClient(Config config) {
        ConnectionProvider provider = ConnectionProvider.builder("upstream")
                .maxConnections(config.getPoolMaxConnections())
                .maxIdleTime(Duration.ofMillis(config.getPoolMaxIdleTime()))
                .maxLifeTime(Duration.ofMillis(config.getPoolMaxLifeTime()))
                .pendingAcquireTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .evictInBackground(Duration.ofMillis(config.getPoolEvictionInterval()))
                .fifo()
                .build();

        return HttpClient.create(provider)
                .responseTimeout(Duration.ofMillis(config.getReadTimeout()));
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
        target.getTotalRequests().incrementAndGet();
        long upstreamStartTime = System.currentTimeMillis();

        URI targetUri = URI.create(target.getUrl());
        String rewrittenPath = exchange.getAttribute("gatewayRewrittenPath");

        String targetPath = targetUri.getPath();
        String requestPath = rewrittenPath != null ? rewrittenPath : exchange.getRequest().fullPath();

        String upstreamPath;
        if (targetPath != null && targetPath.length() > 1) {
            upstreamPath = targetPath + requestPath;
        } else {
            upstreamPath = requestPath;
        }

        URI fullUri = URI.create(targetUri.getScheme() + "://" + targetUri.getHost()
                + ":" + targetUri.getPort() + upstreamPath);

        return httpClient
                .headers(headers -> headers.add(exchange.getRequest().requestHeaders()))
                .request(exchange.getRequest().method())
                .uri(fullUri)
                .send(exchange.getRequest().receive())
                .response((httpClientResponse, byteBufFlux) ->
                        exchange.getResponse()
                                .status(httpClientResponse.status())
                                .headers(httpClientResponse.responseHeaders())
                                .send(byteBufFlux.retain())
                )
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(t -> {
                    target.getActiveConnections().decrementAndGet();
                    long upstreamDuration = System.currentTimeMillis() - upstreamStartTime;
                    MetricsCollector.INSTANCE.recordDuration(
                            "x_gateway_upstream_duration_ms{host=\"" + targetUri.getHost() + ":" + targetUri.getPort() + "\"}",
                            upstreamDuration);
                    HealthChecker hc = HealthChecker.getInstance();
                    if (hc != null) hc.recordResult(target, false);
                    log.error("Fail invoke http endpoint for route:{}, target:{}",
                            exchange.getAttribute(GATEWAY_ROUTE_ATTR), target.getUrl(), t);
                })
                .doOnNext(v -> {
                    target.getActiveConnections().decrementAndGet();
                    long upstreamDuration = System.currentTimeMillis() - upstreamStartTime;
                    MetricsCollector.INSTANCE.recordDuration(
                            "x_gateway_upstream_duration_ms{host=\"" + targetUri.getHost() + ":" + targetUri.getPort() + "\"}",
                            upstreamDuration);
                    HealthChecker hc = HealthChecker.getInstance();
                    if (hc != null) hc.recordResult(target, true);
                    markResponseCommitted(exchange);
                })
                .then();
    }
}
