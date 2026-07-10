package io.github.xinfra.lab.gateway.config;

public interface WritableConfigSource extends ConfigSource {

    void save(GatewayConfig config);
}
