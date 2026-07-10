package io.github.xinfra.lab.gateway.discovery;

import java.util.List;
import java.util.function.Consumer;

public interface ServiceDiscovery {

    List<ServiceInstance> getInstances(String serviceId);

    void subscribe(String serviceId, Consumer<List<ServiceInstance>> listener);

    void start();

    void stop();
}
