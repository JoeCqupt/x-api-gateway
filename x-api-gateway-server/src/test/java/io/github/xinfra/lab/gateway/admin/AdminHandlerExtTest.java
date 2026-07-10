package io.github.xinfra.lab.gateway.admin;

import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.config.GatewayConfigManager;
import io.github.xinfra.lab.gateway.metrics.MetricsCollector;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.netty.http.client.HttpClient;

public class AdminHandlerExtTest {

    private AdminServer adminServer;
    private int adminPort;

    @Before
    public void setup() {
        adminPort = TestSocketUtils.findAvailableTcpPort();

        RouteLocator routeLocator = RouteLocatorBuilder.builder()
                .route("ext-test-route",
                        spec -> spec.path("/api/.*")
                                .endpoint()
                                .http("http://127.0.0.1:9999"))
                .build();

        AdminServer.Config config = new AdminServer.Config();
        config.setEnabled(true);
        config.setPort(adminPort);

        adminServer = new AdminServer(config, routeLocator, new MetricsCollector());
        adminServer.start();
    }

    @After
    public void teardown() {
        if (adminServer != null) {
            adminServer.stop();
        }
    }

    @Test
    public void testFiltersEndpoint() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/filters")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.startsWith("["));
        Assert.assertTrue(body.contains("\"SecureHeaders\"") || body.contains("SecureHeaders"));
    }

    @Test
    public void testPredicatesEndpoint() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/predicates")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.startsWith("["));
        Assert.assertTrue(body.contains("Path") || body.contains("\"Path\""));
    }

    @Test
    public void testEndpointsEndpoint() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/endpoints")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.startsWith("["));
        Assert.assertTrue(body.contains("Http") || body.contains("\"Http\""));
    }

    @Test
    public void testConfigEndpointNoManager() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/config")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertEquals("{}", body);
    }

    @Test
    public void testUpstreamByHost() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/upstream/127.0.0.1")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.startsWith("["));
        Assert.assertTrue(body.contains("127.0.0.1:9999"));
        Assert.assertTrue(body.contains("\"totalRequests\""));
    }

    @Test
    public void testUpstreamByHostNoMatch() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/upstream/10.0.0.1")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertEquals("[]", body);
    }

    @Test
    public void testMetricsJson() {
        String body = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + adminPort + "/admin/metrics/json")
                .responseContent()
                .aggregate()
                .asString()
                .block();

        Assert.assertNotNull(body);
        Assert.assertTrue(body.startsWith("{"));
        Assert.assertTrue(body.contains("\"counters\""));
        Assert.assertTrue(body.contains("\"gauges\""));
        Assert.assertTrue(body.contains("\"histograms\""));
    }

    @Test
    public void testConfigWithFullDetails() {
        int port2 = TestSocketUtils.findAvailableTcpPort();

        RouteLocator rl = RouteLocatorBuilder.builder()
                .route("cfg-route", spec -> spec.path("/test/.*")
                        .endpoint().http("http://127.0.0.1:8888"))
                .build();

        GatewayConfig gc = new GatewayConfig();
        GatewayConfig.ServerConfig sc = new GatewayConfig.ServerConfig();
        sc.setPort(8080);
        sc.setHttp2Enabled(true);
        GatewayConfig.TlsConfig tls = new GatewayConfig.TlsConfig();
        tls.setEnabled(false);
        sc.setTls(tls);
        gc.setServer(sc);

        GatewayConfig.AdminConfig ac = new GatewayConfig.AdminConfig();
        ac.setEnabled(true);
        ac.setPort(port2);
        gc.setAdmin(ac);

        GatewayConfig.UpstreamConfig uc = new GatewayConfig.UpstreamConfig();
        uc.setConnectTimeout(5000);
        gc.setUpstream(uc);

        GatewayConfig.HealthCheckConfig hc = new GatewayConfig.HealthCheckConfig();
        hc.setEnabled(true);
        gc.setHealthCheck(hc);

        GatewayConfig.TracingConfig tc = new GatewayConfig.TracingConfig();
        tc.setEnabled(false);
        gc.setTracing(tc);

        GatewayConfig.PluginConfig pc = new GatewayConfig.PluginConfig();
        pc.setEnabled(false);
        gc.setPlugins(pc);

        GatewayConfig.ObservabilityConfig obs = new GatewayConfig.ObservabilityConfig();
        GatewayConfig.ObservabilityConfig.MetricsConfig mc = new GatewayConfig.ObservabilityConfig.MetricsConfig();
        mc.setEnabled(true);
        mc.setPrefix("my_gw");
        obs.setMetrics(mc);
        GatewayConfig.ObservabilityConfig.AccessLogConfig alc = new GatewayConfig.ObservabilityConfig.AccessLogConfig();
        alc.setEnabled(true);
        alc.setFormat("json");
        obs.setAccessLog(alc);
        gc.setObservability(obs);

        GatewayConfigManager gcm = new GatewayConfigManager();
        gcm.setGatewayConfig(gc);

        AdminServer.Config cfg = new AdminServer.Config();
        cfg.setEnabled(true);
        cfg.setPort(port2);

        AdminServer server2 = new AdminServer(cfg, rl, new MetricsCollector());
        server2.getHandler().setGatewayConfigManager(gcm);
        server2.start();

        try {
            String body = HttpClient.create()
                    .get()
                    .uri("http://127.0.0.1:" + port2 + "/admin/config")
                    .responseContent()
                    .aggregate()
                    .asString()
                    .block();

            Assert.assertNotNull(body);
            Assert.assertTrue(body.contains("\"server\""));
            Assert.assertTrue(body.contains("\"bossThreads\""));
            Assert.assertTrue(body.contains("\"tls\""));
            Assert.assertTrue(body.contains("\"upstream\""));
            Assert.assertTrue(body.contains("\"connectTimeout\":5000"));
            Assert.assertTrue(body.contains("\"healthCheck\""));
            Assert.assertTrue(body.contains("\"tracing\""));
            Assert.assertTrue(body.contains("\"plugins\""));
            Assert.assertTrue(body.contains("\"observability\""));
            Assert.assertTrue(body.contains("\"metrics\""));
            Assert.assertTrue(body.contains("\"my_gw\""));
            Assert.assertTrue(body.contains("\"accessLog\""));
        } finally {
            server2.stop();
        }
    }
}
