package io.github.xinfra.lab.gateway.config;

import io.github.xinfra.lab.gateway.route.RouteDefinition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.github.xinfra.lab.gateway.endpoint.EndpointDefinition;
import io.github.xinfra.lab.gateway.filter.GatewayFilterDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigSourceTest {

    private Path tempDir;
    private Path configFile;

    @Before
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("config-source-test");
        configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, "routes: []\n");
    }

    @After
    public void cleanup() throws IOException {
        Files.deleteIfExists(configFile);
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void testFileConfigSourceLoad() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        GatewayConfig config = source.load();
        Assert.assertNotNull(config);
    }

    @Test
    public void testFileConfigSourceName() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        Assert.assertEquals("file", source.name());
    }

    @Test
    public void testFileConfigSourceOrder() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        Assert.assertEquals(100, source.order());
    }

    @Test
    public void testFileConfigSourceGetPath() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        Assert.assertEquals(configFile.toString(), source.getConfigPath());
    }

    @Test
    public void testFileConfigSourceStartStop() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        source.watch(config -> {});
        source.stop();
    }

    @Test
    public void testConfigManagerAddConfigSource() {
        GatewayConfigManager manager = new GatewayConfigManager();
        FileConfigSource source = new FileConfigSource(configFile.toString());
        manager.addConfigSource(source);
        Assert.assertEquals(1, manager.getConfigSources().size());
    }

    @Test
    public void testMergeConfigRoutesUnion() {
        GatewayConfig base = new GatewayConfig();
        RouteDefinition rd1 = new RouteDefinition();
        rd1.setId("route-1");
        base.setRoutes(Collections.singletonList(rd1));

        GatewayConfig overlay = new GatewayConfig();
        RouteDefinition rd2 = new RouteDefinition();
        rd2.setId("route-2");
        overlay.setRoutes(Collections.singletonList(rd2));

        GatewayConfig merged = GatewayConfigManager.mergeConfig(base, overlay);
        Assert.assertEquals(2, merged.getRoutes().size());
    }

    @Test
    public void testMergeConfigOverlayWins() {
        GatewayConfig base = new GatewayConfig();
        GatewayConfig.ServerConfig baseServer = new GatewayConfig.ServerConfig();
        baseServer.setPort(8080);
        base.setServer(baseServer);

        GatewayConfig overlay = new GatewayConfig();
        GatewayConfig.ServerConfig overlayServer = new GatewayConfig.ServerConfig();
        overlayServer.setPort(9090);
        overlay.setServer(overlayServer);

        GatewayConfig merged = GatewayConfigManager.mergeConfig(base, overlay);
        Assert.assertEquals(9090, merged.getServer().getPort());
    }

    @Test
    public void testMergeConfigNullBase() {
        GatewayConfig overlay = new GatewayConfig();
        GatewayConfig merged = GatewayConfigManager.mergeConfig(null, overlay);
        Assert.assertSame(overlay, merged);
    }

    @Test
    public void testMergeConfigNullOverlay() {
        GatewayConfig base = new GatewayConfig();
        GatewayConfig merged = GatewayConfigManager.mergeConfig(base, null);
        Assert.assertSame(base, merged);
    }

    @Test
    public void testMergeConfigUpstreamAndObservability() {
        GatewayConfig base = new GatewayConfig();
        GatewayConfig.UpstreamConfig baseUpstream = new GatewayConfig.UpstreamConfig();
        baseUpstream.setConnectTimeout(3000);
        base.setUpstream(baseUpstream);

        GatewayConfig overlay = new GatewayConfig();
        GatewayConfig.ObservabilityConfig overlayObs = new GatewayConfig.ObservabilityConfig();
        GatewayConfig.ObservabilityConfig.MetricsConfig metrics = new GatewayConfig.ObservabilityConfig.MetricsConfig();
        metrics.setPrefix("custom_gw");
        overlayObs.setMetrics(metrics);
        overlay.setObservability(overlayObs);

        GatewayConfig merged = GatewayConfigManager.mergeConfig(base, overlay);
        Assert.assertNotNull(merged.getUpstream());
        Assert.assertEquals(3000, merged.getUpstream().getConnectTimeout());
        Assert.assertNotNull(merged.getObservability());
        Assert.assertEquals("custom_gw", merged.getObservability().getMetrics().getPrefix());
    }

    @Test
    public void testMergeConfigUpstreamOverlayWins() {
        GatewayConfig base = new GatewayConfig();
        GatewayConfig.UpstreamConfig baseUp = new GatewayConfig.UpstreamConfig();
        baseUp.setConnectTimeout(3000);
        base.setUpstream(baseUp);

        GatewayConfig overlay = new GatewayConfig();
        GatewayConfig.UpstreamConfig overlayUp = new GatewayConfig.UpstreamConfig();
        overlayUp.setConnectTimeout(5000);
        overlay.setUpstream(overlayUp);

        GatewayConfig merged = GatewayConfigManager.mergeConfig(base, overlay);
        Assert.assertEquals(5000, merged.getUpstream().getConnectTimeout());
    }

    @Test
    public void testFileConfigSourceIsWritable() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        Assert.assertTrue(source instanceof WritableConfigSource);
    }

    @Test
    public void testFileConfigSourceSave() throws IOException {
        FileConfigSource source = new FileConfigSource(configFile.toString());

        GatewayConfig config = new GatewayConfig();
        GatewayConfig.ServerConfig server = new GatewayConfig.ServerConfig();
        server.setPort(9999);
        config.setServer(server);

        RouteDefinition rd = new RouteDefinition();
        rd.setId("saved-route");
        config.setRoutes(Collections.singletonList(rd));

        source.save(config);

        String content = new String(Files.readAllBytes(configFile));
        Assert.assertTrue(content.contains("9999"));
        Assert.assertTrue(content.contains("saved-route"));
    }

    @Test
    public void testFileConfigSourceSaveAndReload() {
        FileConfigSource source = new FileConfigSource(configFile.toString());

        GatewayConfig config = new GatewayConfig();
        RouteDefinition rd = new RouteDefinition();
        rd.setId("persist-route");
        rd.setOrder(5);
        config.setRoutes(Collections.singletonList(rd));

        source.save(config);

        GatewayConfig reloaded = source.load();
        Assert.assertNotNull(reloaded);
        Assert.assertNotNull(reloaded.getRoutes());
        Assert.assertEquals(1, reloaded.getRoutes().size());
        Assert.assertEquals("persist-route", reloaded.getRoutes().get(0).getId());
    }

    @Test
    public void testFileConfigSourceSaveNotifiesListener() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        AtomicReference<GatewayConfig> notified = new AtomicReference<>();
        source.watch(notified::set);

        GatewayConfig config = new GatewayConfig();
        config.setRoutes(Collections.emptyList());
        source.save(config);

        Assert.assertNotNull(notified.get());
        Assert.assertSame(config, notified.get());
    }

    @Test
    public void testFileConfigSourceSaveWithoutListener() {
        FileConfigSource source = new FileConfigSource(configFile.toString());
        GatewayConfig config = new GatewayConfig();
        source.save(config);
    }

    @Test
    public void testFileConfigSourceSaveMultipleRoutes() {
        FileConfigSource source = new FileConfigSource(configFile.toString());

        GatewayConfig config = new GatewayConfig();
        RouteDefinition rd1 = new RouteDefinition();
        rd1.setId("route-a");
        RouteDefinition rd2 = new RouteDefinition();
        rd2.setId("route-b");
        config.setRoutes(Arrays.asList(rd1, rd2));

        source.save(config);

        GatewayConfig reloaded = source.load();
        Assert.assertEquals(2, reloaded.getRoutes().size());
    }

    @Test
    public void testMergeConfigSameRouteIdOverlayWins() {
        GatewayConfig base = new GatewayConfig();
        RouteDefinition rd1 = new RouteDefinition();
        rd1.setId("route-1");
        base.setRoutes(Collections.singletonList(rd1));

        GatewayConfig overlay = new GatewayConfig();
        RouteDefinition rd1Overlay = new RouteDefinition();
        rd1Overlay.setId("route-1");
        overlay.setRoutes(Collections.singletonList(rd1Overlay));

        GatewayConfig merged = GatewayConfigManager.mergeConfig(base, overlay);
        Assert.assertEquals(1, merged.getRoutes().size());
        Assert.assertSame(rd1Overlay, merged.getRoutes().get(0));
    }
}
