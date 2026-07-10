package io.github.xinfra.lab.gateway.plugin;

import io.github.xinfra.lab.gateway.config.ConfigSource;
import io.github.xinfra.lab.gateway.endpoint.EndpointFactory;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactory;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.predicate.RoutePredicateFactory;
import io.github.xinfra.lab.gateway.serializer.Serializer;

import java.util.concurrent.ScheduledExecutorService;

public interface PluginContext {

    void registerFilter(GatewayFilterFactory<?> factory);

    void registerPredicate(RoutePredicateFactory<?> factory);

    void registerEndpoint(EndpointFactory<?> factory);

    void registerConfigSource(ConfigSource source);

    void registerAdminEndpoint(String path, Object handler);

    MetricsCollector metricsCollector();

    Serializer serializer();

    ScheduledExecutorService scheduler();
}
