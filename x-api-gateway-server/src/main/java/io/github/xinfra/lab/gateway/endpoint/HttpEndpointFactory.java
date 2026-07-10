package io.github.xinfra.lab.gateway.endpoint;


import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpEndpointFactory extends
        AbstractConfigurable<HttpEndpoint.Config>
        implements EndpointFactory<HttpEndpoint.Config> {
    public static final String NAME = "Http";

    private ServiceDiscovery serviceDiscovery;

    public HttpEndpointFactory() {
        super(HttpEndpoint.Config.class);
    }

    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Endpoint apply(HttpEndpoint.Config config) {
        if (config.getServiceId() != null && serviceDiscovery != null) {
            return new HttpEndpoint(config, serviceDiscovery);
        }
        return new HttpEndpoint(config);
    }
}
