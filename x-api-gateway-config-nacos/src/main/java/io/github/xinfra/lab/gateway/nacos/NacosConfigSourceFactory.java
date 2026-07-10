package io.github.xinfra.lab.gateway.nacos;

import io.github.xinfra.lab.gateway.config.GatewayConfig;

public class NacosConfigSourceFactory {

    public static NacosConfigSource create(GatewayConfig.NacosConfig config) {
        if (config == null || config.getServerAddr() == null || config.getServerAddr().isEmpty()) {
            throw new IllegalArgumentException("Nacos serverAddr is required");
        }
        return new NacosConfigSource(
                config.getServerAddr(),
                config.getDataId(),
                config.getGroup(),
                config.getNamespace());
    }
}
