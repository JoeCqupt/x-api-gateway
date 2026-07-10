package io.github.xinfra.lab.gateway.admin;

import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.config.GatewayConfigManager;
import io.github.xinfra.lab.gateway.endpoint.EndpointDefinition;
import io.github.xinfra.lab.gateway.filter.ConfigurableGatewayFilter;
import io.github.xinfra.lab.gateway.filter.GatewayFilterDefinition;
import io.github.xinfra.lab.gateway.filter.RateLimitGatewayFilterFactory;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.route.CacheRouteLocator;
import io.github.xinfra.lab.gateway.route.RouteDefinition;
import io.github.xinfra.lab.gateway.route.RouteDefinitionRouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AdminFilterConfigTest {

    private AdminServer adminServer;
    private int adminPort;
    private CacheRouteLocator cacheRouteLocator;

    @Before
    public void setup() {
        adminPort = TestSocketUtils.findAvailableTcpPort();

        RouteDefinition rd = new RouteDefinition();
        rd.setId("test-route");
        rd.setOrder(1);

        List<GatewayFilterDefinition> filterDefs = new ArrayList<>();
        GatewayFilterDefinition rateLimitDef = new GatewayFilterDefinition();
        rateLimitDef.setName("RateLimit");
        rateLimitDef.setConfig("replenishRate: 100\nburstCapacity: 200");
        filterDefs.add(rateLimitDef);

        GatewayFilterDefinition ipFilterDef = new GatewayFilterDefinition();
        ipFilterDef.setName("IpFilter");
        ipFilterDef.setConfig("whitelist:\n  - 10.0.0.0/8");
        filterDefs.add(ipFilterDef);

        rd.setFilters(filterDefs);

        EndpointDefinition endpoint = new EndpointDefinition();
        endpoint.setName("Http");
        endpoint.setConfig("urls:\n  - http://localhost:8080");
        rd.setEndpoint(endpoint);

        RouteDefinitionRouteLocator defLocator = new RouteDefinitionRouteLocator(() -> Flux.just(rd));
        cacheRouteLocator = new CacheRouteLocator(defLocator);

        AdminServer.Config config = new AdminServer.Config();
        config.setEnabled(true);
        config.setPort(adminPort);

        adminServer = new AdminServer(config, cacheRouteLocator, new MetricsCollector());

        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.setRoutes(new ArrayList<>());
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
    public void testGetFilterConfig() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/test-route/filters/RateLimit/config")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("replenishRate"));
        Assert.assertTrue(body.contains("100"));
        Assert.assertTrue(body.contains("burstCapacity"));
        Assert.assertTrue(body.contains("200"));
    }

    @Test
    public void testGetIpFilterConfig() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/test-route/filters/IpFilter/config")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("whitelist"));
        Assert.assertTrue(body.contains("10.0.0.0/8"));
    }

    @Test
    public void testUpdateRateLimitConfig() {
        String newConfig = "replenishRate: 50\nburstCapacity: 100";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "text/plain"))
                .put()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/test-route/filters/RateLimit/config")
                .send(Mono.just(Unpooled.wrappedBuffer(newConfig.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(200, status);

        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/test-route/filters/RateLimit/config")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("50"));
    }

    @Test
    public void testUpdateIpFilterConfig() {
        String newConfig = "whitelist:\n  - 192.168.0.0/16\n  - 10.0.0.0/8";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "text/plain"))
                .put()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/test-route/filters/IpFilter/config")
                .send(Mono.just(Unpooled.wrappedBuffer(newConfig.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(200, status);

        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/test-route/filters/IpFilter/config")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("192.168.0.0/16"));
    }

    @Test
    public void testGetFilterConfigRouteNotFound() {
        int status = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/nonexistent/filters/RateLimit/config")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(404, status);
    }

    @Test
    public void testGetFilterConfigFilterNotFound() {
        int status = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/test-route/filters/NonExistent/config")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(404, status);
    }

    @Test
    public void testUpdateFilterConfigRouteNotFound() {
        String newConfig = "replenishRate: 50";

        int status = HttpClient.create()
                .headers(h -> h.add("Content-Type", "text/plain"))
                .put()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/nonexistent/filters/RateLimit/config")
                .send(Mono.just(Unpooled.wrappedBuffer(newConfig.getBytes(StandardCharsets.UTF_8))))
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(404, status);
    }
}
