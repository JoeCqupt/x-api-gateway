package io.github.xinfra.lab.gateway.config;

import java.util.function.Consumer;

public interface ConfigSource {

    String name();

    GatewayConfig load();

    void watch(Consumer<GatewayConfig> listener);

    int order();
}
