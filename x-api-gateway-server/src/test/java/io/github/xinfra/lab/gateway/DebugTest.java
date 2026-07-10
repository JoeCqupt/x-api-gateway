package io.github.xinfra.lab.gateway;

import io.github.xinfra.lab.gateway.bootstrap.GatewayServerBootstrap;
import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.server.HttpServer;

public class DebugTest {

    @Test
    public void testFullPipeline() {
        int httpServerPort = TestSocketUtils.findAvailableTcpPort();
        int gatewayPort = TestSocketUtils.findAvailableTcpPort();

        // start upstream
        DisposableServer upstream = HttpServer.create()
                .port(httpServerPort)
                .route(routes -> routes.get("/hello",
                        (req, resp) -> resp.sendString(Mono.just("hello!"))))
                .bind().block();

        // build route
        RouteLocator routeLocator = RouteLocatorBuilder.builder()
                .route("test-route",
                        spec -> spec.path("/hello.*")
                                .endpoint()
                                .http("http://127.0.0.1:" + httpServerPort)
                ).build();

        // start gateway
        GatewayServerBootstrap gateway = new GatewayServerBootstrap()
                .port(gatewayPort)
                .routeLocator(routeLocator)
                .start();

        // test request
        HttpClientResponse response = HttpClient.create()
                .get()
                .uri("http://127.0.0.1:" + gatewayPort + "/hello")
                .response()
                .block();
        Assert.assertEquals("200 OK", response.status().toString());

        upstream.disposeNow();
        gateway.stop();
    }
}
