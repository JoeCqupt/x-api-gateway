package io.github.xinfra.lab.gateway.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.config.WritableConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class NacosConfigSource implements WritableConfigSource {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);

    private final ConfigService configService;
    private final String dataId;
    private final String group;
    private final Yaml yaml = new Yaml();
    private volatile Consumer<GatewayConfig> listener;

    public NacosConfigSource(String serverAddr, String dataId, String group) {
        this(serverAddr, dataId, group, null);
    }

    public NacosConfigSource(String serverAddr, String dataId, String group, String namespace) {
        this.dataId = dataId;
        this.group = group;

        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        if (namespace != null && !namespace.isEmpty()) {
            properties.put("namespace", namespace);
        }

        try {
            this.configService = NacosFactory.createConfigService(properties);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to create Nacos ConfigService: " + serverAddr, e);
        }
    }

    NacosConfigSource(ConfigService configService, String dataId, String group) {
        this.configService = configService;
        this.dataId = dataId;
        this.group = group;
    }

    @Override
    public String name() {
        return "nacos";
    }

    @Override
    public GatewayConfig load() {
        try {
            String content = configService.getConfig(dataId, group, 5000);
            if (content == null || content.isEmpty()) {
                log.warn("Empty config from Nacos: dataId={}, group={}", dataId, group);
                return new GatewayConfig();
            }
            return yaml.loadAs(content, GatewayConfig.class);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to load config from Nacos: dataId=" + dataId, e);
        }
    }

    @Override
    public void save(GatewayConfig config) {
        try {
            String content = yaml.dump(config);
            boolean success = configService.publishConfig(dataId, group, content);
            if (success) {
                log.info("Config published to Nacos: dataId={}, group={}", dataId, group);
            } else {
                log.error("Failed to publish config to Nacos: dataId={}, group={}", dataId, group);
            }
        } catch (NacosException e) {
            throw new RuntimeException("Failed to publish config to Nacos", e);
        }
    }

    @Override
    public void watch(Consumer<GatewayConfig> listener) {
        this.listener = listener;
        try {
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    try {
                        GatewayConfig newConfig = yaml.loadAs(configInfo, GatewayConfig.class);
                        if (NacosConfigSource.this.listener != null) {
                            NacosConfigSource.this.listener.accept(newConfig);
                        }
                        log.info("Config updated from Nacos: dataId={}, group={}", dataId, group);
                    } catch (Exception e) {
                        log.error("Failed to parse config from Nacos", e);
                    }
                }
            });
            log.info("Watching Nacos config: dataId={}, group={}", dataId, group);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to watch Nacos config", e);
        }
    }

    @Override
    public int order() {
        return 50;
    }

    public String getDataId() {
        return dataId;
    }

    public String getGroup() {
        return group;
    }
}
