package io.github.xinfra.lab.gateway.config;

import io.github.xinfra.lab.gateway.route.CacheRouteLocator;
import io.github.xinfra.lab.gateway.route.GatewayConfigRouteDefinitionLocator;
import io.github.xinfra.lab.gateway.route.RouteDefinitionRouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ConfigFileWatcher {

    private final String configPath;
    private final GatewayConfigManager configManager;
    private final CacheRouteLocator cacheRouteLocator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread watchThread;
    private volatile long lastReloadTime = 0;
    private static final long DEBOUNCE_MS = 1000;

    public ConfigFileWatcher(String configPath, GatewayConfigManager configManager,
                             CacheRouteLocator cacheRouteLocator) {
        this.configPath = configPath;
        this.configManager = configManager;
        this.cacheRouteLocator = cacheRouteLocator;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        watchThread = new Thread(this::watchLoop, "config-file-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        log.info("Config file watcher started for: {}", configPath);
    }

    public void stop() {
        running.set(false);
        if (watchThread != null) {
            watchThread.interrupt();
            log.info("Config file watcher stopped");
        }
    }

    private void watchLoop() {
        Path filePath = Paths.get(configPath).toAbsolutePath();
        Path parentDir = filePath.getParent();
        String fileName = filePath.getFileName().toString();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    if (!running.get()) break;
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    Path changed = (Path) event.context();
                    if (fileName.equals(changed.toString())) {
                        long now = System.currentTimeMillis();
                        if (now - lastReloadTime > DEBOUNCE_MS) {
                            lastReloadTime = now;
                            reload();
                        }
                    }
                }
                key.reset();
            }
        } catch (IOException e) {
            log.error("Config file watcher failed", e);
        }
    }

    void reload() {
        try {
            log.info("Config file changed, reloading: {}", configPath);
            configManager.load(configPath);
            cacheRouteLocator.refresh();
            log.info("Config reloaded successfully");
        } catch (Exception e) {
            log.error("Config reload failed, keeping old config", e);
        }
    }
}
