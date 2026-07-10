package io.github.xinfra.lab.gateway.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class StaticServiceDiscovery implements ServiceDiscovery {

    private final Map<String, List<ServiceInstance>> registry = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<List<ServiceInstance>>>> listeners = new ConcurrentHashMap<>();

    public void registerInstances(String serviceId, List<ServiceInstance> instances) {
        registry.put(serviceId, new ArrayList<>(instances));
        List<Consumer<List<ServiceInstance>>> subscribers = listeners.get(serviceId);
        if (subscribers != null) {
            for (Consumer<List<ServiceInstance>> listener : subscribers) {
                listener.accept(instances);
            }
        }
    }

    public void registerFromUrls(String serviceId, List<String> urls) {
        List<ServiceInstance> instances = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            URI uri = URI.create(urls.get(i));
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : (
                    "https".equals(uri.getScheme()) ? 443 : 80);
            ServiceInstance instance = new ServiceInstance(serviceId + "-" + i, host, port);
            instances.add(instance);
        }
        registerInstances(serviceId, instances);
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        List<ServiceInstance> instances = registry.get(serviceId);
        return instances != null ? Collections.unmodifiableList(instances) : Collections.emptyList();
    }

    @Override
    public void subscribe(String serviceId, Consumer<List<ServiceInstance>> listener) {
        listeners.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(listener);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        registry.clear();
        listeners.clear();
    }
}
