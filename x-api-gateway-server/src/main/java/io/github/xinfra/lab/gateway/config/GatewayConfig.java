package io.github.xinfra.lab.gateway.config;

import io.github.xinfra.lab.gateway.filter.GatewayFilterDefinition;
import io.github.xinfra.lab.gateway.route.RouteDefinition;
import lombok.Data;

import java.util.List;

@Data
public class GatewayConfig {

    private ServerConfig server;
    private AdminConfig admin;
    private HealthCheckConfig healthCheck;
    private PluginConfig plugins;
    private TracingConfig tracing;
    private ObservabilityConfig observability;
    private UpstreamConfig upstream;
    private List<GatewayFilterDefinition> defaultFilters;
    private List<RouteDefinition> routes;
    private NacosConfig nacos;

    @Data
    public static class ServerConfig {
        private int port = 8080;
        private long shutdownTimeoutMs = 30000;
        private boolean http2Enabled = false;
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int maxContentLength = 10 * 1024 * 1024;
        private int backlog = 1024;
        private boolean tcpNoDelay = true;
        private boolean soKeepAlive = true;
        private TlsConfig tls;
    }

    @Data
    public static class AdminConfig {
        private boolean enabled = false;
        private int port = 9090;
        private String accessToken;
    }

    @Data
    public static class HealthCheckConfig {
        private boolean enabled = false;
        private long intervalMs = 10000;
        private String path = "/health";
        private int unhealthyThreshold = 3;
        private int healthyThreshold = 2;
        private long timeoutMs = 3000;
    }

    @Data
    public static class PluginConfig {
        private boolean enabled = false;
        private String pluginsDir = "plugins";
    }

    @Data
    public static class TracingConfig {
        private boolean enabled = false;
        private String serviceName = "x-api-gateway";
    }

    @Data
    public static class UpstreamConfig {
        private int connectTimeout = 3000;
        private int readTimeout = 10000;
        private int poolMaxConnections = 256;
        private int poolMaxIdleTime = 60000;
        private int poolMaxLifeTime = 300000;
        private int poolEvictionInterval = 30000;
    }

    @Data
    public static class ObservabilityConfig {
        private MetricsConfig metrics;
        private AccessLogConfig accessLog;

        @Data
        public static class MetricsConfig {
            private boolean enabled = true;
            private String exporterType = "prometheus";
            private String prefix = "x_gateway";
        }

        @Data
        public static class AccessLogConfig {
            private boolean enabled = true;
            private String format = "json";
            private String output = "stdout";
            private String filePath;
        }
    }

    @Data
    public static class NacosConfig {
        private boolean enabled = false;
        private boolean discoveryEnabled = false;
        private String serverAddr;
        private String dataId = "x-api-gateway";
        private String group = "DEFAULT_GROUP";
        private String namespace;
    }

    @Data
    public static class TlsConfig {
        private boolean enabled = false;
        private String certPath;
        private String keyPath;
        private List<String> protocols;
    }
}
