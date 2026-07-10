package io.github.xinfra.lab.gateway.admin;

import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class AdminServerTest {

    private AdminServer adminServer;
    private int adminPort;
    private MetricsCollector metricsCollector;

    @Before
    public void setup() {
        adminPort = TestSocketUtils.findAvailableTcpPort();
        metricsCollector = new MetricsCollector();

        RouteLocator routeLocator = RouteLocatorBuilder.builder()
                .route("admin-test-route",
                        spec -> spec.path("/api/.*")
                                .endpoint()
                                .http("http://127.0.0.1:9999"))
                .build();

        AdminServer.Config config = new AdminServer.Config();
        config.setEnabled(true);
        config.setPort(adminPort);
        config.setAccessToken("test-token");

        adminServer = new AdminServer(config, routeLocator, metricsCollector);
        adminServer.start();
    }

    @After
    public void teardown() {
        if (adminServer != null) {
            adminServer.stop();
        }
    }

    @Test
    public void testHealthEndpoint() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/health")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("\"status\":\"UP\""));
    }

    @Test
    public void testUnauthorizedWithoutToken() {
        int status = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(401, status);
    }

    @Test
    public void testRoutesWithToken() {
        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("admin-test-route"));
    }

    @Test
    public void testInfoEndpoint() {
        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/info")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("x-api-gateway"));
        Assert.assertTrue(body.contains("uptime"));
    }

    @Test
    public void testReadyEndpoint() {
        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/ready")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("READY"));
    }

    @Test
    public void testMetricsEndpoint() {
        metricsCollector.recordRequest("test-route", 200, "GET", 5);

        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/metrics")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("x_gateway_requests_total"));
    }

    @Test
    public void testUpstreamEndpoint() {
        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/upstream")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("http://127.0.0.1:9999"));
        Assert.assertTrue(body.contains("\"healthy\":true"));
        Assert.assertTrue(body.contains("\"totalRequests\":"));
    }

    @Test
    public void testRouteByIdEndpoint() {
        String body = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/admin-test-route")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("admin-test-route"));
    }

    @Test
    public void testRouteNotFound() {
        int status = HttpClient.create()
                .headers(h -> h.add("Authorization", "Bearer test-token"))
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/routes/nonexistent")
                .response()
                .map(r -> r.status().code())
                .block();

        Assert.assertEquals(404, status);
    }
}
