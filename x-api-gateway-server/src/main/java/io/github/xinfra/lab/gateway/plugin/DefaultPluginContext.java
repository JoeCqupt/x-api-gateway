package io.github.xinfra.lab.gateway.plugin;

import io.github.xinfra.lab.gateway.config.ConfigSource;
import io.github.xinfra.lab.gateway.endpoint.EndpointFactory;
import io.github.xinfra.lab.gateway.endpoint.EndpointFactoryManager;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactory;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactoryManager;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.predicate.RoutePredicateFactory;
import io.github.xinfra.lab.gateway.predicate.RoutePredicateFactoryManager;
import io.github.xinfra.lab.gateway.serializer.Serializer;
import io.github.xinfra.lab.gateway.serializer.Serializers;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public class DefaultPluginContext implements PluginContext {

    private final List<ConfigSource> configSources = new ArrayList<>();
    private final Map<String, Object> adminEndpoints = new HashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "plugin-scheduler");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void registerFilter(GatewayFilterFactory<?> factory) {
        GatewayFilterFactoryManager.INSTANCE.register(factory.getName(), factory);
    }

    @Override
    public void registerPredicate(RoutePredicateFactory<?> factory) {
        RoutePredicateFactoryManager.INSTANCE.register(factory.getName(), factory);
    }

    @Override
    public void registerEndpoint(EndpointFactory<?> factory) {
        EndpointFactoryManager.INSTANCE.register(factory.getName(), factory);
    }

    @Override
    public void registerConfigSource(ConfigSource source) {
        configSources.add(source);
        log.info("Plugin registered config source: {}", source.name());
    }

    @Override
    public void registerAdminEndpoint(String path, Object handler) {
        adminEndpoints.put(path, handler);
        log.info("Plugin registered admin endpoint: {}", path);
    }

    @Override
    public MetricsCollector metricsCollector() {
        return MetricsCollector.INSTANCE;
    }

    @Override
    public Serializer serializer() {
        return Serializers.jsonSerializer();
    }

    @Override
    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public List<ConfigSource> getConfigSources() {
        return configSources;
    }

    public Map<String, Object> getAdminEndpoints() {
        return adminEndpoints;
    }
}
