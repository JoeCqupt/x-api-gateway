package io.github.xinfra.lab.gateway.healthcheck;

import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class HealthCheckerTest {

    @Test
    public void testHealthyUpstreamStaysHealthy() throws Exception {
        int upstreamPort = TestSocketUtils.findAvailableTcpPort();

        DisposableServer upstream = HttpServer.create()
                .port(upstreamPort)
                .route(routes -> routes.get("/health",
                        (req, resp) -> resp.sendString(Mono.just("OK"))))
                .bind().block();

        RouteLocator locator = RouteLocatorBuilder.builder()
                .route("hc-test", spec -> spec.path("/api/.*")
                        .endpoint()
                        .http("http://127.0.0.1:" + upstreamPort))
                .build();

        HealthChecker.Config config = new HealthChecker.Config();
        config.setEnabled(true);
        config.setIntervalMs(500);
        config.setPath("/health");
        config.setUnhealthyThreshold(2);
        config.setHealthyThreshold(1);
        config.setTimeoutMs(2000);

        HealthChecker checker = new HealthChecker(config, locator);
        checker.checkAll();

        // Get the target and verify it's still healthy
        io.github.xinfra.lab.gateway.route.Route route = locator.getRoutes().next().block();
        io.github.xinfra.lab.gateway.endpoint.HttpEndpoint endpoint =
                (io.github.xinfra.lab.gateway.endpoint.HttpEndpoint) route.getEndpoint();
        UpstreamTarget target = endpoint.getTargets().get(0);

        Assert.assertTrue("Target should be healthy", target.isHealthy());

        upstream.disposeNow();
    }

    @Test
    public void testUnhealthyUpstreamMarkedDown() throws Exception {
        int upstreamPort = TestSocketUtils.findAvailableTcpPort();

        // No server running on this port — health check will fail
        RouteLocator locator = RouteLocatorBuilder.builder()
                .route("hc-test-down", spec -> spec.path("/api/.*")
                        .endpoint()
                        .http("http://127.0.0.1:" + upstreamPort))
                .build();

        HealthChecker.Config config = new HealthChecker.Config();
        config.setEnabled(true);
        config.setIntervalMs(500);
        config.setPath("/health");
        config.setUnhealthyThreshold(2);
        config.setHealthyThreshold(1);
        config.setTimeoutMs(1000);

        HealthChecker checker = new HealthChecker(config, locator);

        // Run health check twice (threshold = 2)
        checker.checkAll();
        checker.checkAll();

        io.github.xinfra.lab.gateway.route.Route route = locator.getRoutes().next().block();
        io.github.xinfra.lab.gateway.endpoint.HttpEndpoint endpoint =
                (io.github.xinfra.lab.gateway.endpoint.HttpEndpoint) route.getEndpoint();
        UpstreamTarget target = endpoint.getTargets().get(0);

        Assert.assertFalse("Target should be unhealthy after 2 failures", target.isHealthy());
    }

    @Test
    public void testRecoveryAfterFailure() throws Exception {
        int upstreamPort = TestSocketUtils.findAvailableTcpPort();

        RouteLocator locator = RouteLocatorBuilder.builder()
                .route("hc-test-recover", spec -> spec.path("/api/.*")
                        .endpoint()
                        .http("http://127.0.0.1:" + upstreamPort))
                .build();

        HealthChecker.Config config = new HealthChecker.Config();
        config.setEnabled(true);
        config.setIntervalMs(500);
        config.setPath("/health");
        config.setUnhealthyThreshold(1);
        config.setHealthyThreshold(1);
        config.setTimeoutMs(1000);

        HealthChecker checker = new HealthChecker(config, locator);

        // Fail — no server running
        checker.checkAll();

        io.github.xinfra.lab.gateway.route.Route route = locator.getRoutes().next().block();
        io.github.xinfra.lab.gateway.endpoint.HttpEndpoint endpoint =
                (io.github.xinfra.lab.gateway.endpoint.HttpEndpoint) route.getEndpoint();
        UpstreamTarget target = endpoint.getTargets().get(0);
        Assert.assertFalse("Should be unhealthy", target.isHealthy());

        // Start the server
        DisposableServer upstream = HttpServer.create()
                .port(upstreamPort)
                .route(routes -> routes.get("/health",
                        (req, resp) -> resp.sendString(Mono.just("OK"))))
                .bind().block();

        // Succeed — server now running
        checker.checkAll();
        Assert.assertTrue("Should recover to healthy", target.isHealthy());

        upstream.disposeNow();
    }
}
