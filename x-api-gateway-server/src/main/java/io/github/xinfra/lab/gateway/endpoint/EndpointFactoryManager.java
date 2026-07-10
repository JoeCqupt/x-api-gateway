package io.github.xinfra.lab.gateway.endpoint;

import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class EndpointFactoryManager {

    public static final EndpointFactoryManager INSTANCE = new EndpointFactoryManager();

    private Map<String, EndpointFactory> factoryMap = new HashMap<>();

    public EndpointFactoryManager() {
        scan(EndpointFactoryManager.class.getPackage().getName());
    }

    public EndpointFactory lookup(String name) {
        return factoryMap.get(name);
    }

    public void register(String name, EndpointFactory factory) {
        factoryMap.put(name, factory);
    }

    public Map<String, EndpointFactory> getFactoryMap() {
        return factoryMap;
    }

    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        for (EndpointFactory factory : factoryMap.values()) {
            if (factory instanceof HttpEndpointFactory) {
                ((HttpEndpointFactory) factory).setServiceDiscovery(serviceDiscovery);
            } else if (factory instanceof WebSocketEndpointFactory) {
                ((WebSocketEndpointFactory) factory).setServiceDiscovery(serviceDiscovery);
            }
        }
        log.info("ServiceDiscovery set on endpoint factories");
    }

    public void scan(String packageName) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<? extends EndpointFactory>> subTypes = reflections.getSubTypesOf(EndpointFactory.class);
        subTypes.forEach(clazz -> {
            try {
                EndpointFactory endpointFactory = clazz.newInstance();
                factoryMap.put(endpointFactory.getName(), endpointFactory);
            } catch (InstantiationException | IllegalAccessException e) {
                log.warn("newInstance for class: {} fail.", clazz.getName(), e);
            }
        });
        log.info("scan EndpointFactory result:{}", factoryMap);
    }
}
