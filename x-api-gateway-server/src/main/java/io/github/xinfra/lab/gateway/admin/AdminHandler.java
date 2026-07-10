package io.github.xinfra.lab.gateway.admin;

import com.google.gson.Gson;
import io.github.xinfra.lab.gateway.commons.binder.Binder;
import io.github.xinfra.lab.gateway.commons.binder.YmlBinder;
import io.github.xinfra.lab.gateway.config.GatewayConfigManager;
import io.github.xinfra.lab.gateway.config.WritableConfigSource;
import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import io.github.xinfra.lab.gateway.discovery.ServiceInstance;
import io.github.xinfra.lab.gateway.endpoint.EndpointFactoryManager;
import io.github.xinfra.lab.gateway.endpoint.HttpEndpoint;
import io.github.xinfra.lab.gateway.filter.ConfigurableGatewayFilter;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.filter.GatewayFilterDefinition;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactory;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactoryManager;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.plugin.GatewayPlugin;
import io.github.xinfra.lab.gateway.plugin.PluginManager;
import io.github.xinfra.lab.gateway.predicate.RoutePredicateFactoryManager;
import io.github.xinfra.lab.gateway.route.CacheRouteLocator;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.route.RouteDefinition;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
public class AdminHandler {

    private final RouteLocator routeLocator;
    private final MetricsCollector metricsCollector;
    private final String accessToken;
    private final Instant startTime = Instant.now();
    private final Gson gson = new Gson();
    private PluginManager pluginManager;
    private GatewayConfigManager gatewayConfigManager;
    private WritableConfigSource writableConfigSource;
    private ServiceDiscovery serviceDiscovery;

    public AdminHandler(RouteLocator routeLocator, MetricsCollector metricsCollector, String accessToken) {
        this.routeLocator = routeLocator;
        this.metricsCollector = metricsCollector;
        this.accessToken = accessToken;
    }

    public void setPluginManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public void setGatewayConfigManager(GatewayConfigManager gatewayConfigManager) {
        this.gatewayConfigManager = gatewayConfigManager;
    }

    public void setWritableConfigSource(WritableConfigSource writableConfigSource) {
        this.writableConfigSource = writableConfigSource;
    }

    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    public Publisher<Void> handle(HttpServerRequest request, HttpServerResponse response) {
        String path = request.fullPath();

        if ("/admin/health".equals(path)) {
            return jsonResponse(response, HttpResponseStatus.OK, "{\"status\":\"UP\"}");
        }

        if (accessToken != null && !accessToken.isEmpty()) {
            if (!authenticate(request)) {
                return jsonResponse(response, HttpResponseStatus.UNAUTHORIZED,
                        "{\"code\":401,\"message\":\"Unauthorized\"}");
            }
        }

        if ("/admin/ready".equals(path)) {
            return handleReady(response);
        }
        if ("/admin/info".equals(path)) {
            return handleInfo(response);
        }
        if ("/admin/routes".equals(path)) {
            if ("POST".equalsIgnoreCase(request.method().name())) {
                return handleAddRoute(request, response);
            }
            return handleRoutes(response);
        }
        if (path != null && path.startsWith("/admin/routes/")) {
            String remainder = path.substring("/admin/routes/".length());

            int filtersIdx = remainder.indexOf("/filters/");
            if (filtersIdx > 0) {
                String routeId = remainder.substring(0, filtersIdx);
                String afterFilters = remainder.substring(filtersIdx + "/filters/".length());
                if (afterFilters.endsWith("/config")) {
                    String filterName = afterFilters.substring(0, afterFilters.length() - "/config".length());
                    String method = request.method().name();
                    if ("PUT".equalsIgnoreCase(method)) {
                        return handleUpdateFilterConfig(request, response, routeId, filterName);
                    }
                    return handleGetFilterConfig(response, routeId, filterName);
                }
            }

            String routeId = remainder;
            if ("refresh".equals(routeId) && "POST".equalsIgnoreCase(request.method().name())) {
                return handleRoutesRefresh(response);
            }
            String method = request.method().name();
            if ("PUT".equalsIgnoreCase(method)) {
                return handleUpdateRoute(request, response, routeId);
            }
            if ("DELETE".equalsIgnoreCase(method)) {
                return handleDeleteRoute(response, routeId);
            }
            return handleRouteById(response, routeId);
        }
        if (path != null && path.startsWith("/admin/upstream/")) {
            String host = path.substring("/admin/upstream/".length());
            return handleUpstreamByHost(response, host);
        }
        if ("/admin/upstream".equals(path)) {
            return handleUpstream(response);
        }
        if ("/admin/metrics/json".equals(path)) {
            return handleMetricsJson(response);
        }
        if ("/admin/metrics".equals(path)) {
            return handleMetrics(response);
        }
        if ("/admin/plugins".equals(path)) {
            return handlePlugins(response);
        }
        if ("/admin/filters".equals(path)) {
            return handleFilters(response);
        }
        if ("/admin/predicates".equals(path)) {
            return handlePredicates(response);
        }
        if ("/admin/endpoints".equals(path)) {
            return handleEndpoints(response);
        }
        if ("/admin/config".equals(path)) {
            if ("POST".equalsIgnoreCase(request.method().name())) {
                return handleConfigReload(response);
            }
            return handleConfig(response);
        }
        if ("/admin/config/reload".equals(path) && "POST".equalsIgnoreCase(request.method().name())) {
            return handleConfigReload(response);
        }
        if (path != null && path.startsWith("/admin/services/")) {
            String serviceId = path.substring("/admin/services/".length());
            return handleServiceInstances(response, serviceId);
        }

        return jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                "{\"code\":404,\"message\":\"Not Found\"}");
    }

    private boolean authenticate(HttpServerRequest request) {
        String auth = request.requestHeaders().get("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                auth.substring(7).getBytes(StandardCharsets.UTF_8),
                accessToken.getBytes(StandardCharsets.UTF_8));
    }

    private Publisher<Void> handleReady(HttpServerResponse response) {
        return routeLocator.getRoutes().collectList()
                .flatMap(routes -> {
                    if (routes != null && !routes.isEmpty()) {
                        return Mono.from(jsonResponse(response, HttpResponseStatus.OK,
                                "{\"status\":\"READY\",\"routeCount\":" + routes.size() + "}"));
                    }
                    return Mono.from(jsonResponse(response, HttpResponseStatus.SERVICE_UNAVAILABLE,
                            "{\"status\":\"NOT_READY\",\"routeCount\":0}"));
                });
    }

    private Publisher<Void> handleInfo(HttpServerResponse response) {
        Duration uptime = Duration.between(startTime, Instant.now());
        String json = "{\"name\":\"x-api-gateway\""
                + ",\"startTime\":\"" + startTime.toString() + "\""
                + ",\"uptime\":\"" + uptime.toString() + "\""
                + "}";
        return jsonResponse(response, HttpResponseStatus.OK, json);
    }

    private Publisher<Void> handleRoutes(HttpServerResponse response) {
        return routeLocator.getRoutes().collectList()
                .flatMap(routes -> {
                    StringBuilder sb = new StringBuilder("[");
                    if (routes != null) {
                        for (int i = 0; i < routes.size(); i++) {
                            if (i > 0) sb.append(',');
                            sb.append(routeToJson(routes.get(i)));
                        }
                    }
                    sb.append(']');
                    return Mono.from(jsonResponse(response, HttpResponseStatus.OK, sb.toString()));
                });
    }

    private Publisher<Void> handleRouteById(HttpServerResponse response, String routeId) {
        return routeLocator.getRoutes()
                .filter(r -> routeId.equals(r.getId()))
                .next()
                .flatMap(found -> Mono.from(jsonResponse(response, HttpResponseStatus.OK, routeToJson(found))))
                .switchIfEmpty(Mono.defer(() -> Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                        "{\"code\":404,\"message\":\"Route not found: " + routeId + "\"}"))));
    }

    private Publisher<Void> handleRoutesRefresh(HttpServerResponse response) {
        if (routeLocator instanceof CacheRouteLocator) {
            refreshRoutes();
            log.info("Routes refreshed via Admin API");
            return jsonResponse(response, HttpResponseStatus.OK,
                    "{\"message\":\"Routes refreshed\"}");
        }
        return jsonResponse(response, HttpResponseStatus.OK,
                "{\"message\":\"RouteLocator does not support refresh\"}");
    }

    private Publisher<Void> handleUpstream(HttpServerResponse response) {
        return routeLocator.getRoutes().collectList()
                .flatMap(routes -> {
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    if (routes != null) {
                        for (Route route : routes) {
                            if (route.getEndpoint() instanceof HttpEndpoint) {
                                HttpEndpoint endpoint = (HttpEndpoint) route.getEndpoint();
                                for (UpstreamTarget target : endpoint.getTargets()) {
                                    if (!first) sb.append(',');
                                    first = false;
                                    sb.append("{\"routeId\":\"").append(escape(route.getId())).append('"');
                                    sb.append(",\"url\":\"").append(escape(target.getUrl())).append('"');
                                    sb.append(",\"weight\":").append(target.getWeight());
                                    sb.append(",\"healthy\":").append(target.isHealthy());
                                    sb.append(",\"activeConnections\":").append(target.getActiveConnections().get());
                                    sb.append(",\"totalRequests\":").append(target.getTotalRequests().get());
                                    sb.append('}');
                                }
                            }
                        }
                    }
                    sb.append(']');
                    return Mono.from(jsonResponse(response, HttpResponseStatus.OK, sb.toString()));
                });
    }

    private Publisher<Void> handlePlugins(HttpServerResponse response) {
        StringBuilder sb = new StringBuilder("[");
        if (pluginManager != null) {
            List<GatewayPlugin> plugins = pluginManager.getLoadedPlugins();
            for (int i = 0; i < plugins.size(); i++) {
                if (i > 0) sb.append(',');
                GatewayPlugin p = plugins.get(i);
                sb.append("{\"name\":\"").append(escape(p.name())).append('"');
                sb.append(",\"version\":\"").append(escape(p.version())).append('"');
                sb.append(",\"started\":").append(pluginManager.isStarted());
                sb.append('}');
            }
        }
        sb.append(']');
        return jsonResponse(response, HttpResponseStatus.OK, sb.toString());
    }

    private Publisher<Void> handleAddRoute(HttpServerRequest request, HttpServerResponse response) {
        if (gatewayConfigManager == null) {
            return jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"Config manager not available\"}");
        }
        return request.receive().aggregate().asString()
                .flatMap(body -> {
                    try {
                        RouteDefinition rd = gson.fromJson(body, RouteDefinition.class);
                        if (rd.getId() == null || rd.getId().isEmpty()) {
                            return Mono.from(jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                                    "{\"code\":400,\"message\":\"Route id is required\"}"));
                        }
                        if (rd.getEndpoint() == null) {
                            return Mono.from(jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                                    "{\"code\":400,\"message\":\"Route endpoint is required\"}"));
                        }
                        List<RouteDefinition> routes = gatewayConfigManager.getGatewayConfig().getRoutes();
                        if (routes == null) {
                            routes = new ArrayList<>();
                            gatewayConfigManager.getGatewayConfig().setRoutes(routes);
                        }
                        for (RouteDefinition existing : routes) {
                            if (rd.getId().equals(existing.getId())) {
                                return Mono.from(jsonResponse(response, HttpResponseStatus.CONFLICT,
                                        "{\"code\":409,\"message\":\"Route already exists: " + escape(rd.getId()) + "\"}"));
                            }
                        }
                        routes.add(rd);
                        refreshRoutes();
                        persistConfig();
                        log.info("Route added via Admin API: {}", rd.getId());
                        return Mono.from(jsonResponse(response, HttpResponseStatus.CREATED,
                                "{\"message\":\"Route added\",\"id\":\"" + escape(rd.getId()) + "\"}"));
                    } catch (Exception e) {
                        log.error("Failed to add route", e);
                        return Mono.from(jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                                "{\"code\":400,\"message\":\"Invalid route definition: " + escape(e.getMessage()) + "\"}"));
                    }
                });
    }

    private Publisher<Void> handleUpdateRoute(HttpServerRequest request, HttpServerResponse response, String routeId) {
        if (gatewayConfigManager == null) {
            return jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"Config manager not available\"}");
        }
        return request.receive().aggregate().asString()
                .flatMap(body -> {
                    try {
                        RouteDefinition rd = gson.fromJson(body, RouteDefinition.class);
                        rd.setId(routeId);
                        List<RouteDefinition> routes = gatewayConfigManager.getGatewayConfig().getRoutes();
                        if (routes == null) {
                            return Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                                    "{\"code\":404,\"message\":\"Route not found: " + escape(routeId) + "\"}"));
                        }
                        boolean found = false;
                        for (int i = 0; i < routes.size(); i++) {
                            if (routeId.equals(routes.get(i).getId())) {
                                routes.set(i, rd);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            return Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                                    "{\"code\":404,\"message\":\"Route not found: " + escape(routeId) + "\"}"));
                        }
                        refreshRoutes();
                        persistConfig();
                        log.info("Route updated via Admin API: {}", routeId);
                        return Mono.from(jsonResponse(response, HttpResponseStatus.OK,
                                "{\"message\":\"Route updated\",\"id\":\"" + escape(routeId) + "\"}"));
                    } catch (Exception e) {
                        log.error("Failed to update route", e);
                        return Mono.from(jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                                "{\"code\":400,\"message\":\"Invalid route definition: " + escape(e.getMessage()) + "\"}"));
                    }
                });
    }

    private Publisher<Void> handleDeleteRoute(HttpServerResponse response, String routeId) {
        if (gatewayConfigManager == null) {
            return jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"Config manager not available\"}");
        }
        List<RouteDefinition> routes = gatewayConfigManager.getGatewayConfig().getRoutes();
        if (routes == null) {
            return jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"Route not found: " + escape(routeId) + "\"}");
        }
        boolean removed = false;
        Iterator<RouteDefinition> it = routes.iterator();
        while (it.hasNext()) {
            if (routeId.equals(it.next().getId())) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (!removed) {
            return jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"Route not found: " + escape(routeId) + "\"}");
        }
        refreshRoutes();
        persistConfig();
        log.info("Route deleted via Admin API: {}", routeId);
        return jsonResponse(response, HttpResponseStatus.OK,
                "{\"message\":\"Route deleted\",\"id\":\"" + escape(routeId) + "\"}");
    }

    private Publisher<Void> handleUpstreamByHost(HttpServerResponse response, String host) {
        return routeLocator.getRoutes().collectList()
                .flatMap(routes -> {
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    if (routes != null) {
                        for (Route route : routes) {
                            if (route.getEndpoint() instanceof HttpEndpoint) {
                                HttpEndpoint endpoint = (HttpEndpoint) route.getEndpoint();
                                for (UpstreamTarget target : endpoint.getTargets()) {
                                    if (target.getUrl() != null && target.getUrl().contains(host)) {
                                        if (!first) sb.append(',');
                                        first = false;
                                        sb.append("{\"routeId\":\"").append(escape(route.getId())).append('"');
                                        sb.append(",\"url\":\"").append(escape(target.getUrl())).append('"');
                                        sb.append(",\"weight\":").append(target.getWeight());
                                        sb.append(",\"healthy\":").append(target.isHealthy());
                                        sb.append(",\"activeConnections\":").append(target.getActiveConnections().get());
                                        sb.append(",\"totalRequests\":").append(target.getTotalRequests().get());
                                        sb.append('}');
                                    }
                                }
                            }
                        }
                    }
                    sb.append(']');
                    return Mono.from(jsonResponse(response, HttpResponseStatus.OK, sb.toString()));
                });
    }

    private Publisher<Void> handleMetricsJson(HttpServerResponse response) {
        return jsonResponse(response, HttpResponseStatus.OK, metricsCollector.exportJson());
    }

    private Publisher<Void> handleServiceInstances(HttpServerResponse response, String serviceId) {
        if (serviceDiscovery == null) {
            return jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"Service discovery not available\"}");
        }
        List<ServiceInstance> instances = serviceDiscovery.getInstances(serviceId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < instances.size(); i++) {
            if (i > 0) sb.append(',');
            ServiceInstance inst = instances.get(i);
            sb.append("{\"instanceId\":\"").append(escape(inst.getInstanceId())).append('"');
            sb.append(",\"host\":\"").append(escape(inst.getHost())).append('"');
            sb.append(",\"port\":").append(inst.getPort());
            sb.append(",\"healthy\":").append(inst.isHealthy());
            sb.append('}');
        }
        sb.append(']');
        return jsonResponse(response, HttpResponseStatus.OK, sb.toString());
    }

    private void refreshRoutes() {
        if (routeLocator instanceof CacheRouteLocator) {
            Thread t = new Thread(() -> ((CacheRouteLocator) routeLocator).refresh(),
                    "admin-route-refresh");
            t.setDaemon(true);
            t.start();
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void persistConfig() {
        if (writableConfigSource != null && gatewayConfigManager != null) {
            try {
                writableConfigSource.save(gatewayConfigManager.getGatewayConfig());
            } catch (Exception e) {
                log.error("Failed to persist config", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Publisher<Void> handleGetFilterConfig(HttpServerResponse response, String routeId, String filterName) {
        return routeLocator.getRoutes()
                .filter(r -> routeId.equals(r.getId()))
                .next()
                .flatMap(route -> {
                    if (route.getFilters() == null) {
                        return Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                                "{\"code\":404,\"message\":\"No filters on route: " + escape(routeId) + "\"}"));
                    }
                    for (GatewayFilter filter : route.getFilters()) {
                        if (filter instanceof ConfigurableGatewayFilter) {
                            ConfigurableGatewayFilter<?> cf = (ConfigurableGatewayFilter<?>) filter;
                            if (filterName.equals(cf.getFilterName())) {
                                String configJson = gson.toJson(cf.getConfig());
                                return Mono.from(jsonResponse(response, HttpResponseStatus.OK, configJson));
                            }
                        }
                    }
                    return Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                            "{\"code\":404,\"message\":\"Filter not found: " + escape(filterName) + "\"}"));
                })
                .switchIfEmpty(Mono.defer(() -> Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                        "{\"code\":404,\"message\":\"Route not found: " + escape(routeId) + "\"}"))));
    }

    @SuppressWarnings("unchecked")
    private Publisher<Void> handleUpdateFilterConfig(HttpServerRequest request, HttpServerResponse response,
            String routeId, String filterName) {
        return request.receive().aggregate().asString()
                .flatMap(body -> routeLocator.getRoutes()
                        .filter(r -> routeId.equals(r.getId()))
                        .next()
                        .flatMap(route -> {
                            if (route.getFilters() == null) {
                                return Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                                        "{\"code\":404,\"message\":\"No filters on route: " + escape(routeId) + "\"}"));
                            }
                            for (GatewayFilter filter : route.getFilters()) {
                                if (filter instanceof ConfigurableGatewayFilter) {
                                    ConfigurableGatewayFilter cf = (ConfigurableGatewayFilter) filter;
                                    if (filterName.equals(cf.getFilterName())) {
                                        try {
                                            GatewayFilterFactory factory = GatewayFilterFactoryManager.INSTANCE.lookup(filterName);
                                            if (factory == null) {
                                                return Mono.from(jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                                                        "{\"code\":400,\"message\":\"Unknown filter factory: " + escape(filterName) + "\"}"));
                                            }
                                            Binder binder = new YmlBinder();
                                            Object newConfig = binder.binder(factory.getConfigClass(), body);
                                            cf.updateConfig(newConfig);
                                            updateFilterConfigInDefinition(routeId, filterName, body);
                                            persistConfig();
                                            return Mono.from(jsonResponse(response, HttpResponseStatus.OK,
                                                    "{\"message\":\"Filter config updated\",\"route\":\"" + escape(routeId)
                                                            + "\",\"filter\":\"" + escape(filterName) + "\"}"));
                                        } catch (Exception e) {
                                            return Mono.from(jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                                                    "{\"code\":400,\"message\":\"Invalid config: " + escape(e.getMessage()) + "\"}"));
                                        }
                                    }
                                }
                            }
                            return Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                                    "{\"code\":404,\"message\":\"Configurable filter not found: " + escape(filterName) + "\"}"));
                        })
                        .switchIfEmpty(Mono.defer(() -> Mono.from(jsonResponse(response, HttpResponseStatus.NOT_FOUND,
                                "{\"code\":404,\"message\":\"Route not found: " + escape(routeId) + "\"}")))));
    }

    private void updateFilterConfigInDefinition(String routeId, String filterName, String newConfigStr) {
        if (gatewayConfigManager == null) return;
        List<RouteDefinition> routes = gatewayConfigManager.getGatewayConfig().getRoutes();
        if (routes == null) return;
        for (RouteDefinition rd : routes) {
            if (routeId.equals(rd.getId()) && rd.getFilters() != null) {
                for (GatewayFilterDefinition fd : rd.getFilters()) {
                    if (filterName.equals(fd.getName())) {
                        fd.setConfig(newConfigStr);
                        return;
                    }
                }
            }
        }
    }

    private Publisher<Void> handleFilters(HttpServerResponse response) {
        return jsonResponse(response, HttpResponseStatus.OK,
                toJsonArray(GatewayFilterFactoryManager.INSTANCE.getFactoryMap().keySet()));
    }

    private Publisher<Void> handlePredicates(HttpServerResponse response) {
        return jsonResponse(response, HttpResponseStatus.OK,
                toJsonArray(RoutePredicateFactoryManager.INSTANCE.getFactoryMap().keySet()));
    }

    private Publisher<Void> handleEndpoints(HttpServerResponse response) {
        return jsonResponse(response, HttpResponseStatus.OK,
                toJsonArray(EndpointFactoryManager.INSTANCE.getFactoryMap().keySet()));
    }

    private Publisher<Void> handleConfig(HttpServerResponse response) {
        if (gatewayConfigManager == null || gatewayConfigManager.getGatewayConfig() == null) {
            return jsonResponse(response, HttpResponseStatus.OK, "{}");
        }
        return jsonResponse(response, HttpResponseStatus.OK,
                configToJson(gatewayConfigManager.getGatewayConfig()));
    }

    private Publisher<Void> handleConfigReload(HttpServerResponse response) {
        if (gatewayConfigManager == null || gatewayConfigManager.getConfigPath() == null) {
            return jsonResponse(response, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"No config path configured\"}");
        }
        try {
            gatewayConfigManager.load(gatewayConfigManager.getConfigPath());
            refreshRoutes();
            log.info("Config reloaded via Admin API");
            return jsonResponse(response, HttpResponseStatus.OK,
                    "{\"message\":\"Config reloaded\"}");
        } catch (Exception e) {
            log.error("Failed to reload config", e);
            return jsonResponse(response, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"Failed to reload config: " + escape(e.getMessage()) + "\"}");
        }
    }

    private String toJsonArray(java.util.Set<String> names) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String name : names) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(name)).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private String configToJson(io.github.xinfra.lab.gateway.config.GatewayConfig config) {
        StringBuilder sb = new StringBuilder("{");
        boolean hasField = false;
        if (config.getServer() != null) {
            sb.append("\"server\":{\"port\":").append(config.getServer().getPort());
            sb.append(",\"http2Enabled\":").append(config.getServer().isHttp2Enabled());
            sb.append(",\"bossThreads\":").append(config.getServer().getBossThreads());
            sb.append(",\"workerThreads\":").append(config.getServer().getWorkerThreads());
            sb.append(",\"maxContentLength\":").append(config.getServer().getMaxContentLength());
            sb.append(",\"tcpNoDelay\":").append(config.getServer().isTcpNoDelay());
            sb.append(",\"soKeepAlive\":").append(config.getServer().isSoKeepAlive());
            if (config.getServer().getTls() != null) {
                sb.append(",\"tls\":{\"enabled\":").append(config.getServer().getTls().isEnabled()).append('}');
            }
            sb.append('}');
            hasField = true;
        }
        if (config.getAdmin() != null) {
            if (hasField) sb.append(',');
            sb.append("\"admin\":{\"enabled\":").append(config.getAdmin().isEnabled());
            sb.append(",\"port\":").append(config.getAdmin().getPort());
            sb.append('}');
            hasField = true;
        }
        if (config.getUpstream() != null) {
            if (hasField) sb.append(',');
            sb.append("\"upstream\":{\"connectTimeout\":").append(config.getUpstream().getConnectTimeout());
            sb.append(",\"readTimeout\":").append(config.getUpstream().getReadTimeout());
            sb.append(",\"poolMaxConnections\":").append(config.getUpstream().getPoolMaxConnections());
            sb.append('}');
            hasField = true;
        }
        if (config.getHealthCheck() != null) {
            if (hasField) sb.append(',');
            sb.append("\"healthCheck\":{\"enabled\":").append(config.getHealthCheck().isEnabled());
            sb.append(",\"intervalMs\":").append(config.getHealthCheck().getIntervalMs());
            sb.append(",\"path\":\"").append(escape(config.getHealthCheck().getPath())).append('"');
            sb.append('}');
            hasField = true;
        }
        if (config.getTracing() != null) {
            if (hasField) sb.append(',');
            sb.append("\"tracing\":{\"enabled\":").append(config.getTracing().isEnabled());
            sb.append(",\"serviceName\":\"").append(escape(config.getTracing().getServiceName())).append('"');
            sb.append('}');
            hasField = true;
        }
        if (config.getPlugins() != null) {
            if (hasField) sb.append(',');
            sb.append("\"plugins\":{\"enabled\":").append(config.getPlugins().isEnabled());
            sb.append(",\"pluginsDir\":\"").append(escape(config.getPlugins().getPluginsDir())).append('"');
            sb.append('}');
            hasField = true;
        }
        if (config.getObservability() != null) {
            if (hasField) sb.append(',');
            sb.append("\"observability\":{");
            io.github.xinfra.lab.gateway.config.GatewayConfig.ObservabilityConfig obs = config.getObservability();
            boolean obsField = false;
            if (obs.getMetrics() != null) {
                sb.append("\"metrics\":{\"enabled\":").append(obs.getMetrics().isEnabled());
                sb.append(",\"exporterType\":\"").append(escape(obs.getMetrics().getExporterType())).append('"');
                sb.append(",\"prefix\":\"").append(escape(obs.getMetrics().getPrefix())).append('"');
                sb.append('}');
                obsField = true;
            }
            if (obs.getAccessLog() != null) {
                if (obsField) sb.append(',');
                sb.append("\"accessLog\":{\"enabled\":").append(obs.getAccessLog().isEnabled());
                sb.append(",\"format\":\"").append(escape(obs.getAccessLog().getFormat())).append('"');
                sb.append(",\"output\":\"").append(escape(obs.getAccessLog().getOutput())).append('"');
                sb.append('}');
            }
            sb.append('}');
            hasField = true;
        }
        if (config.getRoutes() != null) {
            if (hasField) sb.append(',');
            sb.append("\"routeCount\":").append(config.getRoutes().size());
        }
        sb.append('}');
        return sb.toString();
    }

    private Publisher<Void> handleMetrics(HttpServerResponse response) {
        String body = metricsCollector.exportPrometheus();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.status(HttpResponseStatus.OK)
                .addHeader("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                .sendByteArray(Mono.just(bytes))
                .then();
    }

    private Publisher<Void> jsonResponse(HttpServerResponse response, HttpResponseStatus status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.status(status)
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .sendByteArray(Mono.just(bytes))
                .then();
    }

    private String routeToJson(Route route) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(escape(route.getId())).append('"');
        sb.append(",\"filterCount\":").append(route.getFilters() != null ? route.getFilters().size() : 0);
        if (route.getEndpoint() instanceof HttpEndpoint) {
            HttpEndpoint he = (HttpEndpoint) route.getEndpoint();
            sb.append(",\"endpoint\":{\"type\":\"Http\",\"targets\":[");
            List<UpstreamTarget> targets = he.getTargets();
            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append("{\"url\":\"").append(escape(targets.get(i).getUrl())).append('"');
                sb.append(",\"weight\":").append(targets.get(i).getWeight());
                sb.append(",\"healthy\":").append(targets.get(i).isHealthy());
                sb.append('}');
            }
            sb.append("]}");
        }
        if (route.getMetadata() != null && !route.getMetadata().isEmpty()) {
            sb.append(",\"metadata\":{");
            boolean first = true;
            for (java.util.Map.Entry<String, Object> entry : route.getMetadata().entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(entry.getKey())).append("\":\"")
                        .append(escape(String.valueOf(entry.getValue()))).append('"');
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
