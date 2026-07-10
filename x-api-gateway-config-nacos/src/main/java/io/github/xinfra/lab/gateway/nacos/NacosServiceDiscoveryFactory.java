package io.github.xinfra.lab.gateway.nacos;

import io.github.xinfra.lab.gateway.config.GatewayConfig;

public class NacosServiceDiscoveryFactory {

    public static NacosServiceDiscovery create(GatewayConfig.NacosConfig config) {
        if (config == null || config.getServerAddr() == null || config.getServerAddr().isEmpty()) {
            throw new IllegalArgumentException("Nacos serverAddr is required");
        }
        return new NacosServiceDiscovery(config.getServerAddr(), config.getNamespace());
    }
}
