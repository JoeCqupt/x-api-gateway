package io.github.xinfra.lab.gateway.plugin;

import io.github.xinfra.lab.gateway.commons.OrderedAwareComparator;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

@Slf4j
public class PluginManager {

    private final List<GatewayPlugin> plugins = new ArrayList<>();
    private final PluginContext context;
    private final String pluginsDir;
    private volatile boolean started = false;

    public PluginManager(PluginContext context, String pluginsDir) {
        this.context = context;
        this.pluginsDir = pluginsDir;
    }

    public void loadPlugins() {
        loadClasspathPlugins();
        loadExternalPlugins();

        OrderedAwareComparator.sort(plugins);

        for (GatewayPlugin plugin : plugins) {
            try {
                plugin.onInstall(context);
                log.info("Installed plugin: {} v{}", plugin.name(), plugin.version());
            } catch (Exception e) {
                log.error("Failed to install plugin: {}", plugin.name(), e);
            }
        }
    }

    public void startPlugins() {
        for (GatewayPlugin plugin : plugins) {
            try {
                plugin.onStart(context);
                log.info("Started plugin: {}", plugin.name());
            } catch (Exception e) {
                log.error("Failed to start plugin: {}", plugin.name(), e);
            }
        }
        started = true;
    }

    public void stopPlugins() {
        if (!started) return;
        started = false;

        List<GatewayPlugin> reversed = new ArrayList<>(plugins);
        Collections.reverse(reversed);

        for (GatewayPlugin plugin : reversed) {
            try {
                plugin.onStop(context);
                log.info("Stopped plugin: {}", plugin.name());
            } catch (Exception e) {
                log.error("Failed to stop plugin: {}", plugin.name(), e);
            }
        }

        for (GatewayPlugin plugin : reversed) {
            try {
                plugin.onUninstall(context);
                log.info("Uninstalled plugin: {}", plugin.name());
            } catch (Exception e) {
                log.error("Failed to uninstall plugin: {}", plugin.name(), e);
            }
        }
    }

    private void loadClasspathPlugins() {
        ServiceLoader<GatewayPlugin> loader = ServiceLoader.load(GatewayPlugin.class);
        for (GatewayPlugin plugin : loader) {
            plugins.add(plugin);
            log.info("Discovered classpath plugin: {} v{}", plugin.name(), plugin.version());
        }
    }

    private void loadExternalPlugins() {
        if (pluginsDir == null) return;

        File dir = new File(pluginsDir);
        if (!dir.isDirectory()) {
            log.debug("Plugins directory not found: {}", pluginsDir);
            return;
        }

        File[] jarFiles = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            return;
        }

        for (File jar : jarFiles) {
            try {
                URL jarUrl = jar.toURI().toURL();
                URLClassLoader classLoader = new URLClassLoader(
                        new URL[]{jarUrl}, getClass().getClassLoader());
                ServiceLoader<GatewayPlugin> loader =
                        ServiceLoader.load(GatewayPlugin.class, classLoader);
                for (GatewayPlugin plugin : loader) {
                    plugins.add(plugin);
                    log.info("Discovered external plugin: {} v{} from {}",
                            plugin.name(), plugin.version(), jar.getName());
                }
            } catch (Exception e) {
                log.error("Failed to load plugin JAR: {}", jar.getName(), e);
            }
        }
    }

    public List<GatewayPlugin> getLoadedPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public boolean isStarted() {
        return started;
    }
}
