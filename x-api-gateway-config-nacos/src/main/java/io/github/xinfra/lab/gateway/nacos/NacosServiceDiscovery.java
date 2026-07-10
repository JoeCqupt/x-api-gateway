package io.github.xinfra.lab.gateway.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.github.xinfra.lab.gateway.discovery.ServiceDiscovery;
import io.github.xinfra.lab.gateway.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class NacosServiceDiscovery implements ServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);

    private final NamingService namingService;

    public NacosServiceDiscovery(String serverAddr, String namespace) {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        if (namespace != null && !namespace.isEmpty()) {
            properties.put("namespace", namespace);
        }
        try {
            this.namingService = NacosFactory.createNamingService(properties);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to create Nacos NamingService: " + serverAddr, e);
        }
    }

    NacosServiceDiscovery(NamingService namingService) {
        this.namingService = namingService;
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        try {
            List<Instance> instances = namingService.selectInstances(serviceId, true);
            return toServiceInstances(instances);
        } catch (NacosException e) {
            log.error("Failed to get instances for service: {}", serviceId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void subscribe(String serviceId, Consumer<List<ServiceInstance>> listener) {
        try {
            EventListener eventListener = event -> {
                if (event instanceof NamingEvent) {
                    NamingEvent namingEvent = (NamingEvent) event;
                    List<ServiceInstance> instances = toServiceInstances(namingEvent.getInstances());
                    listener.accept(instances);
                    log.info("Nacos service instances updated for {}: {} instances",
                            serviceId, instances.size());
                }
            };
            namingService.subscribe(serviceId, eventListener);
            log.info("Subscribed to Nacos service: {}", serviceId);
        } catch (NacosException e) {
            log.error("Failed to subscribe to service: {}", serviceId, e);
        }
    }

    @Override
    public void start() {
        log.info("NacosServiceDiscovery started");
    }

    @Override
    public void stop() {
        try {
            namingService.shutDown();
            log.info("NacosServiceDiscovery stopped");
        } catch (NacosException e) {
            log.error("Failed to shutdown NacosServiceDiscovery", e);
        }
    }

    private static List<ServiceInstance> toServiceInstances(List<Instance> instances) {
        if (instances == null) {
            return Collections.emptyList();
        }
        List<ServiceInstance> result = new ArrayList<>();
        for (Instance inst : instances) {
            ServiceInstance si = new ServiceInstance(
                    inst.getInstanceId(), inst.getIp(), inst.getPort());
            si.setHealthy(inst.isHealthy());
            if (inst.getMetadata() != null) {
                si.getMetadata().putAll(inst.getMetadata());
            }
            result.add(si);
        }
        return result;
    }
}
