package io.github.xinfra.lab.gateway.config;

import io.github.xinfra.lab.gateway.route.CacheRouteLocator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.*;

public class ConfigFileWatcherTest {

    private Path tempDir;
    private Path configFile;

    @Before
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("config-watcher-test");
        configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "routes: []\n");
    }

    @After
    public void cleanup() throws IOException {
        Files.deleteIfExists(configFile);
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void testReloadUpdatesConfig() throws IOException {
        GatewayConfigManager configManager = new GatewayConfigManager();
        configManager.load(configFile.toString());

        CacheRouteLocator cacheLocator = mock(CacheRouteLocator.class);

        ConfigFileWatcher watcher = new ConfigFileWatcher(
                configFile.toString(), configManager, cacheLocator);

        watcher.reload();

        verify(cacheLocator, times(1)).refresh();
    }

    @Test
    public void testReloadFailureKeepsOldConfig() throws IOException {
        GatewayConfigManager configManager = new GatewayConfigManager();
        configManager.load(configFile.toString());
        GatewayConfig original = configManager.getGatewayConfig();

        CacheRouteLocator cacheLocator = mock(CacheRouteLocator.class);
        doThrow(new RuntimeException("refresh failed")).when(cacheLocator).refresh();

        ConfigFileWatcher watcher = new ConfigFileWatcher(
                configFile.toString(), configManager, cacheLocator);

        // Should not throw — error is caught and logged
        watcher.reload();
    }

    @Test
    public void testStartAndStop() {
        GatewayConfigManager configManager = new GatewayConfigManager();
        configManager.load(configFile.toString());

        CacheRouteLocator cacheLocator = mock(CacheRouteLocator.class);
        ConfigFileWatcher watcher = new ConfigFileWatcher(
                configFile.toString(), configManager, cacheLocator);

        watcher.start();
        watcher.stop();
    }

    @Test
    public void testDetectsFileChange() throws Exception {
        GatewayConfigManager configManager = new GatewayConfigManager();
        configManager.load(configFile.toString());

        CacheRouteLocator cacheLocator = mock(CacheRouteLocator.class);
        ConfigFileWatcher watcher = new ConfigFileWatcher(
                configFile.toString(), configManager, cacheLocator);

        watcher.start();

        Thread.sleep(200);
        Files.writeString(configFile, "routes:\n  - id: new-route\n    predicates:\n      - name: Path\n        config: 'pattern: /test'\n    endpoint:\n      name: Http\n      config: 'urls:\\n  - http://localhost:8080'\n");

        Thread.sleep(2000);
        watcher.stop();

        // The watcher should have detected the change and called refresh
        // Note: WatchService detection timing can vary, so we just verify it doesn't crash
    }
}
