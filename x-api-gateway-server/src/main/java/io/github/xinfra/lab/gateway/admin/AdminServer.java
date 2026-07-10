package io.github.xinfra.lab.gateway.admin;

import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.plugin.PluginManager;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.InetSocketAddress;
import java.time.Duration;

@Slf4j
public class AdminServer {

    @Data
    public static class Config {
        private boolean enabled = false;
        private int port = 9090;
        private String accessToken;
    }

    private final Config config;
    private final AdminHandler handler;
    private DisposableServer server;

    public AdminServer(Config config, RouteLocator routeLocator, MetricsCollector metricsCollector) {
        this.config = config;
        this.handler = new AdminHandler(routeLocator, metricsCollector, config.getAccessToken());
    }

    public AdminHandler getHandler() {
        return handler;
    }

    public void start() {
        if (!config.isEnabled()) return;

        this.server = HttpServer.create()
                .handle(handler::handle)
                .bindAddress(() -> new InetSocketAddress(config.getPort()))
                .bind().block();

        log.info("Admin API started on port {}", config.getPort());
    }

    public void stop() {
        if (server != null) {
            server.disposeNow(Duration.ofSeconds(5));
            log.info("Admin API stopped");
        }
    }
}
