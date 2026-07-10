package io.github.xinfra.lab.gateway.healthcheck;

import io.github.xinfra.lab.gateway.endpoint.HttpEndpoint;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class HealthChecker {

    @Data
    public static class Config {
        private boolean enabled = false;
        private long intervalMs = 10000;
        private String path = "/health";
        private int unhealthyThreshold = 3;
        private int healthyThreshold = 2;
        private long timeoutMs = 3000;
    }

    private final Config config;
    private final RouteLocator routeLocator;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> consecutiveSuccesses = new ConcurrentHashMap<>();

    public HealthChecker(Config config, RouteLocator routeLocator) {
        this.config = config;
        this.routeLocator = routeLocator;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-checker");
            t.setDaemon(true);
            return t;
        });
        ConnectionProvider provider = ConnectionProvider.builder("health-check")
                .maxConnections(16)
                .build();
        this.httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofMillis(config.getTimeoutMs()));
    }

    public void start() {
        if (!config.isEnabled()) return;
        log.info("HealthChecker started, interval={}ms, path={}", config.getIntervalMs(), config.getPath());
        scheduler.scheduleAtFixedRate(this::checkAll, config.getIntervalMs(),
                config.getIntervalMs(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void checkAll() {
        try {
            List<Route> routes = routeLocator.getRoutes().collectList().block();
            if (routes == null) return;

            for (Route route : routes) {
                if (route.getEndpoint() instanceof HttpEndpoint) {
                    HttpEndpoint endpoint = (HttpEndpoint) route.getEndpoint();
                    for (UpstreamTarget target : endpoint.getTargets()) {
                        checkTarget(target);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Health check cycle failed", e);
        }
    }

    void checkTarget(UpstreamTarget target) {
        URI targetUri = URI.create(target.getUrl());
        String checkUrl = targetUri.getScheme() + "://" + targetUri.getHost()
                + ":" + targetUri.getPort() + config.getPath();

        try {
            Integer statusCode = httpClient
                    .get()
                    .uri(checkUrl)
                    .response()
                    .map(resp -> resp.status().code())
                    .block(Duration.ofMillis(config.getTimeoutMs() + 1000));

            if (statusCode != null && statusCode >= 200 && statusCode < 300) {
                onSuccess(target);
            } else {
                onFailure(target, "status=" + statusCode);
            }
        } catch (Exception e) {
            onFailure(target, e.getClass().getSimpleName());
        }
    }

    private void onSuccess(UpstreamTarget target) {
        String key = target.getUrl();
        consecutiveFailures.computeIfAbsent(key, k -> new AtomicInteger(0)).set(0);
        int successes = consecutiveSuccesses.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (!target.isHealthy() && successes >= config.getHealthyThreshold()) {
            target.setHealthy(true);
            log.info("Upstream {} recovered after {} consecutive successes", key, successes);
        }
    }

    private void onFailure(UpstreamTarget target, String reason) {
        String key = target.getUrl();
        consecutiveSuccesses.computeIfAbsent(key, k -> new AtomicInteger(0)).set(0);
        int failures = consecutiveFailures.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (target.isHealthy() && failures >= config.getUnhealthyThreshold()) {
            target.setHealthy(false);
            log.warn("Upstream {} marked unhealthy after {} consecutive failures (last: {})",
                    key, failures, reason);
        }
    }

    public void recordResult(UpstreamTarget target, boolean success) {
        if (!config.isEnabled()) return;
        if (success) {
            onSuccess(target);
        } else {
            onFailure(target, "passive");
        }
    }

    private static volatile HealthChecker instance;

    public static void setInstance(HealthChecker checker) {
        instance = checker;
    }

    public static HealthChecker getInstance() {
        return instance;
    }
}
