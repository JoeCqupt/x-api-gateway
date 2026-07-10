package io.github.xinfra.lab.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;

@Slf4j
public class FileConfigSource implements WritableConfigSource {

    private final String configPath;
    private final Yaml yaml = new Yaml();
    private volatile Consumer<GatewayConfig> listener;
    private volatile Thread watchThread;
    private volatile boolean running = false;

    public FileConfigSource(String configPath) {
        this.configPath = configPath;
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public GatewayConfig load() {
        try {
            return yaml.loadAs(Files.newInputStream(Paths.get(configPath)), GatewayConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config from: " + configPath, e);
        }
    }

    @Override
    public void watch(Consumer<GatewayConfig> listener) {
        this.listener = listener;
        startWatching();
    }

    @Override
    public int order() {
        return 100;
    }

    private void startWatching() {
        if (running) return;
        running = true;

        Path filePath = Paths.get(configPath);
        Path dir = filePath.getParent();
        String fileName = filePath.getFileName().toString();

        watchThread = new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                long lastReload = 0;
                while (running) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();
                        if (changed != null && changed.toString().equals(fileName)) {
                            long now = System.currentTimeMillis();
                            if (now - lastReload > 1000) {
                                lastReload = now;
                                try {
                                    GatewayConfig newConfig = load();
                                    if (listener != null) {
                                        listener.accept(newConfig);
                                    }
                                    log.info("Config reloaded from file: {}", configPath);
                                } catch (Exception e) {
                                    log.error("Failed to reload config from: {}", configPath, e);
                                }
                            }
                        }
                    }
                    key.reset();
                }
                watchService.close();
            } catch (IOException e) {
                log.error("Config file watch failed", e);
            }
        }, "config-file-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    @Override
    public void save(GatewayConfig config) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml dumpYaml = new Yaml(new SkipTagRepresenter(options), options);

        try (Writer writer = new FileWriter(configPath)) {
            dumpYaml.dump(config, writer);
            log.info("Config saved to file: {}", configPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config to: " + configPath, e);
        }

        if (listener != null) {
            try {
                listener.accept(config);
            } catch (Exception e) {
                log.error("Error notifying config listener after save", e);
            }
        }
    }

    private static class SkipTagRepresenter extends Representer {
        SkipTagRepresenter(DumperOptions options) {
            super(options);
            this.getPropertyUtils().setSkipMissingProperties(true);
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
                Object propertyValue, Tag customTag) {
            if (propertyValue == null) {
                return null;
            }
            return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
        }

        @Override
        protected org.yaml.snakeyaml.nodes.MappingNode representJavaBean(
                java.util.Set<Property> properties, Object javaBean) {
            if (!classTags.containsKey(javaBean.getClass())) {
                addClassTag(javaBean.getClass(), Tag.MAP);
            }
            return super.representJavaBean(properties, javaBean);
        }
    }

    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    public String getConfigPath() {
        return configPath;
    }
}
