package io.github.xinfra.lab.gateway.discovery;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ServiceInstance {
    private String instanceId;
    private String host;
    private int port;
    private Map<String, String> metadata = new HashMap<>();
    private boolean healthy = true;

    public ServiceInstance() {
    }

    public ServiceInstance(String instanceId, String host, int port) {
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
    }

    public String getAddress() {
        return host + ":" + port;
    }
}
