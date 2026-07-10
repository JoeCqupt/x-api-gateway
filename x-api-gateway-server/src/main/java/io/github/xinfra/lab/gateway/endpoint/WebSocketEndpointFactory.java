package io.github.xinfra.lab.gateway.endpoint;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketEndpointFactory extends
        AbstractConfigurable<WebSocketEndpoint.Config>
        implements EndpointFactory<WebSocketEndpoint.Config> {

    public static final String NAME = "WebSocket";

    private ServiceDiscovery serviceDiscovery;

    public WebSocketEndpointFactory() {
        super(WebSocketEndpoint.Config.class);
    }

    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Endpoint apply(WebSocketEndpoint.Config config) {
        if (config.getServiceId() != null && serviceDiscovery != null) {
            return new WebSocketEndpoint(config, serviceDiscovery);
        }
        return new WebSocketEndpoint(config);
    }
}
