package io.github.xinfra.lab.gateway.admin;

import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.config.FileConfigSource;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.config.GatewayConfigManager;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.route.RouteDefinition;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class AdminPersistenceTest {

    private AdminServer adminServer;
    private int adminPort;
    private Path tempDir;
    private Path configFile;

    @Before
    public void setup() throws IOException {
        adminPort = TestSocketUtils.findAvailableTcpPort();
        tempDir = Files.createTempDirectory("admin-persist-test");
        configFile = tempDir.resolve("gateway-config.yml");
        Files.writeString(configFile, "routes: []\n");

        adminServer = new AdminServer(
                createAdminConfig(),
                RouteLocatorBuilder.builder()
                        .route("existing", spec -> spec.path("/api/.*")
                                .endpoint().http("http://127.0.0.1:9999"))
                        .build(),
                new MetricsCollector());

        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.setRoutes(new ArrayList<>());
        RouteDefinition rd = new RouteDefinition();
        rd.setId("existing");
        gatewayConfig.getRoutes().add(rd);

        GatewayConfigManager configManager = new GatewayConfigManager();
        try {
            java.lang.reflect.Field f = GatewayConfigManager.class.getDeclaredField("config");
            f.setAccessible(true);
            f.set(configManager, gatewayConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        FileConfigSource fileConfigSource = new FileConfigSource(configFile.toString());
        adminServer.getHandler().setGatewayConfigManager(configManager);
        adminServer.getHandler().setWritableConfigSource(fileConfigSource);
        adminServer.start();
    }

    @After
    public void teardown() throws IOException {
        if (adminServer != null) {
            adminServer.stop();
        }
        Files.deleteIfExists(configFile);
        Files.deleteIfExists(tempDir);
    }

    private AdminServer.Config createAdminConfig() {
        AdminServer.Config config = new AdminServer.Config();
        config.setEnabled(true);
        config.setPort(adminPort);
        return config;
    }

    @Test
    public void testAddRoutePersistsToFile() throws IOException {
        String routeJson = "{\"id\":\"persisted-route\",\"endpoint\":{\"name\":\"Http\",\"config\":\"urls:\\n  - http://localhost:8080\"}}";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .post()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(201, status);

        String content = new String(Files.readAllBytes(configFile));
        Assert.assertTrue("Config file should contain persisted route id",
                content.contains("persisted-route"));
    }

    @Test
    public void testDeleteRoutePersistsToFile() throws IOException {
        int status = HttpClient.create()
                .delete()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/existing")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(200, status);

        String content = new String(Files.readAllBytes(configFile));
        Assert.assertFalse("Config file should not contain deleted route",
                content.contains("existing"));
    }

    @Test
    public void testUpdateRoutePersistsToFile() throws IOException {
        String routeJson = "{\"endpoint\":{\"name\":\"Http\",\"config\":\"urls:\\n  - http://localhost:9090\"}}";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .put()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/existing")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(200, status);

        String content = new String(Files.readAllBytes(configFile));
        Assert.assertTrue("Config file should contain updated route",
                content.contains("existing"));
    }

    @Test
    public void testPersistedConfigCanBeReloaded() throws IOException {
        String routeJson = "{\"id\":\"reload-test\",\"order\":5,\"endpoint\":{\"name\":\"Http\",\"config\":\"urls:\\n  - http://localhost:8080\"}}";

        HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .post()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .block();

        FileConfigSource reloadSource = new FileConfigSource(configFile.toString());
        GatewayConfig reloaded = reloadSource.load();
        Assert.assertNotNull(reloaded);
        Assert.assertNotNull(reloaded.getRoutes());

        boolean found = false;
        for (RouteDefinition rd : reloaded.getRoutes()) {
            if ("reload-test".equals(rd.getId())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue("Reloaded config should contain the added route", found);
    }
}
