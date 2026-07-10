package io.github.xinfra.lab.gateway.endpoint;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.github.xinfra.lab.gateway.BaseTest;
import io.github.xinfra.lab.gateway.bootstrap.GatewayServerBootstrap;
import io.github.xinfra.lab.gateway.common.TestSocketUtils;
import io.github.xinfra.lab.gateway.discovery.ServiceInstance;
import io.github.xinfra.lab.gateway.discovery.StaticServiceDiscovery;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.server.HttpServer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


@Slf4j
public class HttpEndpointTest extends BaseTest {

    @Test
    public void httpEndpointFileConfigTest() {

        int httpServerPort = TestSocketUtils.findAvailableTcpPort();
        int gatewayServerPort = TestSocketUtils.findAvailableTcpPort();

        // start upstream httpServer first
        DisposableServer httpServer = HttpServer.create()
                .port(httpServerPort)
                .route(routes ->
                        routes.get("/hello",
                                (httpServerRequest, httpServerResponse) ->
                                        httpServerResponse.sendString(Mono.just("hello world!")))
                ).bind().block();
        log.info("httpServer started on port {}", httpServerPort);

        // update config to use dynamic port
        String configContent = "routes:\n" +
                "  - id: http-test\n" +
                "    predicates:\n" +
                "      - name: Path\n" +
                "        config: 'pattern: /hello.*'\n" +
                "    filters:\n" +
                "      - name: AddHeader\n" +
                "        config: 'proxy: x-api-gateway'\n" +
                "    endpoint:\n" +
                "      name: Http\n" +
                "      config: |\n" +
                "        urls:\n" +
                "          - http://127.0.0.1:" + httpServerPort + "\n";

        java.io.File tempFile;
        try {
            tempFile = java.io.File.createTempFile("gateway-test", ".yml");
            tempFile.deleteOnExit();
            java.nio.file.Files.write(tempFile.toPath(), configContent.getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        GatewayServerBootstrap bootstrap = startGatewayServer(gatewayServerPort, tempFile.getAbsolutePath());
        log.info("gatewayServer started on port {}", gatewayServerPort);

        // test direct access to upstream
        HttpClientResponse response = HttpClient.create()
                .get()
                .uri(String.format("http://localhost:%s/hello", httpServerPort))
                .response()
                .block();
        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        log.info("httpclient request httpServer success.");

        // test access via gateway
        response = HttpClient.create()
                .get()
                .uri(String.format("http://localhost:%s/hello", gatewayServerPort))
                .response()
                .block();
        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        log.info("httpclient request gatewayServer success.");

        httpServer.disposeNow();
        bootstrap.stop();
    }

    @Test
    public void httpEndpointApiConfigTest() {

        int httpServerPort = TestSocketUtils.findAvailableTcpPort();
        int gatewayServerPort = TestSocketUtils.findAvailableTcpPort();

        // start upstream httpServer first
        DisposableServer httpServer = HttpServer.create()
                .port(httpServerPort)
                .route(routes ->
                        routes.get("/hello",
                                (httpServerRequest, httpServerResponse) ->
                                        httpServerResponse.sendString(Mono.just("hello world!")))
                ).bind().block();
        log.info("httpServer started on port {}", httpServerPort);

        Map<String, String> addHeadersMap = Maps.newHashMap();
        addHeadersMap.put("proxy", "x-api-gateway");

        RouteLocator routeLocator = RouteLocatorBuilder.builder()
                .route("http-test",
                        routePredicateSpec ->
                                routePredicateSpec.path("/hello.*")
                                        .filters()
                                        .addHeader(addHeadersMap)
                                        .endpoint()
                                        .http(Lists.newArrayList("http://127.0.0.1:" + httpServerPort),
                                                Lists.newArrayList())
                ).build();

        GatewayServerBootstrap bootstrap = startGatewayServer(gatewayServerPort, routeLocator);
        log.info("gatewayServer started on port {}", gatewayServerPort);

        // test direct access to upstream
        HttpClientResponse response = HttpClient.create()
                .get()
                .uri(String.format("http://localhost:%s/hello", httpServerPort))
                .response()
                .block();
        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        log.info("httpclient request httpServer success.");

        // test access via gateway
        response = HttpClient.create()
                .get()
                .uri(String.format("http://localhost:%s/hello", gatewayServerPort))
                .response()
                .block();
        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        log.info("httpclient request gatewayServer success.");

        httpServer.disposeNow();
        bootstrap.stop();
    }

    @Test
    public void testHttpEndpointWithServiceDiscovery() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst1 = new ServiceInstance("inst-1", "host1", 8080);
        ServiceInstance inst2 = new ServiceInstance("inst-2", "host2", 9090);
        discovery.registerInstances("my-service", Arrays.asList(inst1, inst2));

        HttpEndpoint.Config config = new HttpEndpoint.Config();
        config.setServiceId("my-service");
        config.setLoadBalancer("Random");

        HttpEndpoint endpoint = new HttpEndpoint(config, discovery);
        List<UpstreamTarget> targets = endpoint.getTargets();

        Assert.assertEquals(2, targets.size());
        Assert.assertEquals("http://host1:8080", targets.get(0).getUrl());
        Assert.assertEquals("http://host2:9090", targets.get(1).getUrl());
    }

    @Test
    public void testHttpEndpointServiceDiscoverySubscribeUpdates() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst1 = new ServiceInstance("inst-1", "host1", 8080);
        discovery.registerInstances("dynamic-service", Arrays.asList(inst1));

        HttpEndpoint.Config config = new HttpEndpoint.Config();
        config.setServiceId("dynamic-service");
        config.setLoadBalancer("Random");

        HttpEndpoint endpoint = new HttpEndpoint(config, discovery);
        Assert.assertEquals(1, endpoint.getTargets().size());

        ServiceInstance inst2 = new ServiceInstance("inst-2", "host2", 9090);
        ServiceInstance inst3 = new ServiceInstance("inst-3", "host3", 7070);
        discovery.registerInstances("dynamic-service", Arrays.asList(inst2, inst3));

        Assert.assertEquals(2, endpoint.getTargets().size());
        Assert.assertEquals("http://host2:9090", endpoint.getTargets().get(0).getUrl());
        Assert.assertEquals("http://host3:7070", endpoint.getTargets().get(1).getUrl());
    }

    @Test
    public void testHttpEndpointServiceDiscoveryFiltersUnhealthy() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance healthy = new ServiceInstance("h-1", "host1", 8080);
        ServiceInstance unhealthy = new ServiceInstance("u-1", "host2", 9090);
        unhealthy.setHealthy(false);
        discovery.registerInstances("mixed-service", Arrays.asList(healthy, unhealthy));

        HttpEndpoint.Config config = new HttpEndpoint.Config();
        config.setServiceId("mixed-service");

        HttpEndpoint endpoint = new HttpEndpoint(config, discovery);
        Assert.assertEquals(1, endpoint.getTargets().size());
        Assert.assertEquals("http://host1:8080", endpoint.getTargets().get(0).getUrl());
    }

    @Test
    public void testHttpEndpointFallbackToUrlsWhenNoDiscovery() {
        HttpEndpoint.Config config = new HttpEndpoint.Config();
        config.setServiceId("my-service");
        config.setUrls(Arrays.asList("http://fallback:8080"));

        HttpEndpoint endpoint = new HttpEndpoint(config);
        Assert.assertEquals(1, endpoint.getTargets().size());
        Assert.assertEquals("http://fallback:8080", endpoint.getTargets().get(0).getUrl());
    }

    @Test
    public void testHttpEndpointFactoryWithServiceDiscovery() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst = new ServiceInstance("inst-1", "host1", 8080);
        discovery.registerInstances("factory-service", Arrays.asList(inst));

        HttpEndpointFactory factory = new HttpEndpointFactory();
        factory.setServiceDiscovery(discovery);

        HttpEndpoint.Config config = new HttpEndpoint.Config();
        config.setServiceId("factory-service");

        Endpoint endpoint = factory.apply(config);
        Assert.assertTrue(endpoint instanceof HttpEndpoint);
        HttpEndpoint httpEndpoint = (HttpEndpoint) endpoint;
        Assert.assertEquals(1, httpEndpoint.getTargets().size());
        Assert.assertEquals("http://host1:8080", httpEndpoint.getTargets().get(0).getUrl());
    }
}
