package io.github.xinfra.lab.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GatewayConfigManager {
    public static final String DEFAULT_CONFIG_YML_FILE_NAME = "config.yml";
    private GatewayConfig config;
    private Yaml yaml = new Yaml();
    private String configPath;
    private final List<ConfigSource> configSources = new ArrayList<>();

    /**
     * load default config
     */
    public void loadDefault() {
        java.io.InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(DEFAULT_CONFIG_YML_FILE_NAME);
        if (is == null) {
            throw new RuntimeException("Default config file not found on classpath: " + DEFAULT_CONFIG_YML_FILE_NAME);
        }
        try {
            config = yaml.loadAs(is, GatewayConfig.class);
            if (config == null) {
                config = new GatewayConfig();
            }
        } finally {
            try { is.close(); } catch (IOException ignored) { }
        }
    }

    public void load(String configPath) {
        try {
            this.configPath = configPath;
            config = yaml.loadAs(Files.newInputStream(Paths.get(configPath)), GatewayConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("fail load path:" + configPath, e);
        }
    }

    public void addConfigSource(ConfigSource source) {
        configSources.add(source);
        Collections.sort(configSources, Comparator.comparingInt(ConfigSource::order));
        log.info("Added config source: {} (order={})", source.name(), source.order());
    }

    public GatewayConfig loadFromSources() {
        if (configSources.isEmpty()) {
            return config;
        }

        GatewayConfig merged = null;
        for (ConfigSource source : configSources) {
            try {
                GatewayConfig sourceConfig = source.load();
                if (sourceConfig != null) {
                    merged = mergeConfig(merged, sourceConfig);
                }
            } catch (Exception e) {
                log.error("Failed to load from config source: {}", source.name(), e);
            }
        }

        if (merged != null) {
            this.config = merged;
        }
        return config;
    }

    static GatewayConfig mergeConfig(GatewayConfig base, GatewayConfig overlay) {
        if (base == null) return overlay;
        if (overlay == null) return base;

        GatewayConfig result = new GatewayConfig();

        result.setServer(overlay.getServer() != null ? overlay.getServer() : base.getServer());
        result.setAdmin(overlay.getAdmin() != null ? overlay.getAdmin() : base.getAdmin());
        result.setHealthCheck(overlay.getHealthCheck() != null ? overlay.getHealthCheck() : base.getHealthCheck());
        result.setPlugins(overlay.getPlugins() != null ? overlay.getPlugins() : base.getPlugins());
        result.setTracing(overlay.getTracing() != null ? overlay.getTracing() : base.getTracing());
        result.setUpstream(overlay.getUpstream() != null ? overlay.getUpstream() : base.getUpstream());
        result.setObservability(overlay.getObservability() != null ? overlay.getObservability() : base.getObservability());
        result.setDefaultFilters(overlay.getDefaultFilters() != null
                ? overlay.getDefaultFilters() : base.getDefaultFilters());
        result.setNacos(overlay.getNacos() != null ? overlay.getNacos() : base.getNacos());

        if (base.getRoutes() == null && overlay.getRoutes() == null) {
            result.setRoutes(null);
        } else if (base.getRoutes() == null) {
            result.setRoutes(overlay.getRoutes());
        } else if (overlay.getRoutes() == null) {
            result.setRoutes(base.getRoutes());
        } else {
            Map<String, io.github.xinfra.lab.gateway.route.RouteDefinition> routeMap = new HashMap<>();
            for (io.github.xinfra.lab.gateway.route.RouteDefinition rd : base.getRoutes()) {
                routeMap.put(rd.getId(), rd);
            }
            for (io.github.xinfra.lab.gateway.route.RouteDefinition rd : overlay.getRoutes()) {
                routeMap.put(rd.getId(), rd);
            }
            result.setRoutes(new ArrayList<>(routeMap.values()));
        }

        return result;
    }

    public List<ConfigSource> getConfigSources() {
        return Collections.unmodifiableList(configSources);
    }

    public GatewayConfig getGatewayConfig() {
        return config;
    }

    public void setGatewayConfig(GatewayConfig config) {
        this.config = config;
    }

    public String getConfigPath() {
        return configPath;
    }
}
