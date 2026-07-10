package io.github.xinfra.lab.gateway.admin;

import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.config.GatewayConfigManager;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.route.RouteDefinition;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class AdminDynamicRouteTest {

    private AdminServer adminServer;
    private int adminPort;

    @Before
    public void setup() {
        adminPort = TestSocketUtils.findAvailableTcpPort();

        RouteLocator routeLocator = RouteLocatorBuilder.builder()
                .route("existing-route",
                        spec -> spec.path("/api/.*")
                                .endpoint()
                                .http("http://127.0.0.1:9999"))
                .build();

        AdminServer.Config config = new AdminServer.Config();
        config.setEnabled(true);
        config.setPort(adminPort);

        adminServer = new AdminServer(config, routeLocator, new MetricsCollector());

        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.setRoutes(new ArrayList<>());
        RouteDefinition rd = new RouteDefinition();
        rd.setId("existing-route");
        gatewayConfig.getRoutes().add(rd);

        GatewayConfigManager configManager = new GatewayConfigManager();
        try {
            java.lang.reflect.Field configField = GatewayConfigManager.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(configManager, gatewayConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        adminServer.getHandler().setGatewayConfigManager(configManager);
        adminServer.start();
    }

    @After
    public void teardown() {
        if (adminServer != null) {
            adminServer.stop();
        }
    }

    @Test
    public void testAddRoute() {
        String routeJson = "{\"id\":\"new-route\",\"endpoint\":{\"name\":\"Http\",\"config\":\"targets:\\n  - url: http://localhost:8080\"}}";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .post()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(201, status);
    }

    @Test
    public void testAddDuplicateRoute() {
        String routeJson = "{\"id\":\"existing-route\",\"endpoint\":{\"name\":\"Http\",\"config\":\"targets:\\n  - url: http://localhost:8080\"}}";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .post()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(409, status);
    }

    @Test
    public void testAddRouteNoId() {
        String routeJson = "{\"endpoint\":{\"name\":\"Http\"}}";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .post()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(400, status);
    }

    @Test
    public void testDeleteRoute() {
        int status = HttpClient.create()
                .delete()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/existing-route")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(200, status);
    }

    @Test
    public void testDeleteRouteNotFound() {
        int status = HttpClient.create()
                .delete()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/nonexistent")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(404, status);
    }

    @Test
    public void testUpdateRoute() {
        String routeJson = "{\"endpoint\":{\"name\":\"Http\",\"config\":\"targets:\\n  - url: http://localhost:9090\"}}";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .put()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/existing-route")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(200, status);
    }

    @Test
    public void testUpdateRouteNotFound() {
        String routeJson = "{\"endpoint\":{\"name\":\"Http\"}}";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "application/json"))
                .put()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/nonexistent")
                .send(Mono.just(Unpooled.wrappedBuffer(routeJson.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(404, status);
    }
}
