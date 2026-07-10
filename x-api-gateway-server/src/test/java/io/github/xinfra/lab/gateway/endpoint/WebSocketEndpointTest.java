package io.github.xinfra.lab.gateway.endpoint;

import io.github.xinfra.lab.gateway.discovery.ServiceInstance;
import io.github.xinfra.lab.gateway.discovery.StaticServiceDiscovery;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class WebSocketEndpointTest {

    @Test
    public void testWebSocketEndpointConfig() {
        WebSocketEndpoint.Config config = new WebSocketEndpoint.Config();
        config.setUrls(Arrays.asList("ws://host1:8080", "ws://host2:8080"));
        config.setLoadBalancer("Random");

        WebSocketEndpoint endpoint = new WebSocketEndpoint(config);
        Assert.assertNotNull(endpoint);
        Assert.assertEquals(2, endpoint.getTargets().size());
        Assert.assertEquals("ws://host1:8080", endpoint.getTargets().get(0).getUrl());
    }

    @Test
    public void testWebSocketUpgradeDetection() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("Upgrade", "websocket");
        headers.add("Connection", "Upgrade");
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        Assert.assertTrue(WebSocketEndpoint.isWebSocketUpgrade(exchange));
    }

    @Test
    public void testNonWebSocketRequest() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        Assert.assertFalse(WebSocketEndpoint.isWebSocketUpgrade(exchange));
    }

    @Test
    public void testWebSocketEndpointFactory() {
        WebSocketEndpointFactory factory = new WebSocketEndpointFactory();
        Assert.assertEquals("WebSocket", factory.getName());
        Assert.assertEquals(WebSocketEndpoint.Config.class, factory.getConfigClass());
    }

    @Test
    public void testWebSocketEndpointFactoryCreatesEndpoint() {
        WebSocketEndpointFactory factory = new WebSocketEndpointFactory();
        WebSocketEndpoint.Config config = new WebSocketEndpoint.Config();
        config.setUrls(Arrays.asList("ws://localhost:9090"));

        Endpoint endpoint = factory.apply(config);
        Assert.assertNotNull(endpoint);
        Assert.assertTrue(endpoint instanceof WebSocketEndpoint);
    }

    @Test
    public void testWebSocketEndpointWithServiceDiscovery() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst1 = new ServiceInstance("ws-1", "host1", 8080);
        ServiceInstance inst2 = new ServiceInstance("ws-2", "host2", 9090);
        discovery.registerInstances("ws-service", Arrays.asList(inst1, inst2));

        WebSocketEndpoint.Config config = new WebSocketEndpoint.Config();
        config.setServiceId("ws-service");
        config.setLoadBalancer("Random");

        WebSocketEndpoint endpoint = new WebSocketEndpoint(config, discovery);
        List<UpstreamTarget> targets = endpoint.getTargets();

        Assert.assertEquals(2, targets.size());
        Assert.assertEquals("ws://host1:8080", targets.get(0).getUrl());
        Assert.assertEquals("ws://host2:9090", targets.get(1).getUrl());
    }

    @Test
    public void testWebSocketEndpointServiceDiscoveryUpdates() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst1 = new ServiceInstance("ws-1", "host1", 8080);
        discovery.registerInstances("ws-dynamic", Arrays.asList(inst1));

        WebSocketEndpoint.Config config = new WebSocketEndpoint.Config();
        config.setServiceId("ws-dynamic");

        WebSocketEndpoint endpoint = new WebSocketEndpoint(config, discovery);
        Assert.assertEquals(1, endpoint.getTargets().size());

        ServiceInstance inst2 = new ServiceInstance("ws-2", "host2", 9090);
        discovery.registerInstances("ws-dynamic", Arrays.asList(inst2));

        Assert.assertEquals(1, endpoint.getTargets().size());
        Assert.assertEquals("ws://host2:9090", endpoint.getTargets().get(0).getUrl());
    }

    @Test
    public void testWebSocketEndpointFactoryWithServiceDiscovery() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst = new ServiceInstance("ws-1", "host1", 8080);
        discovery.registerInstances("ws-factory-svc", Arrays.asList(inst));

        WebSocketEndpointFactory factory = new WebSocketEndpointFactory();
        factory.setServiceDiscovery(discovery);

        WebSocketEndpoint.Config config = new WebSocketEndpoint.Config();
        config.setServiceId("ws-factory-svc");

        Endpoint endpoint = factory.apply(config);
        Assert.assertTrue(endpoint instanceof WebSocketEndpoint);
        Assert.assertEquals(1, ((WebSocketEndpoint) endpoint).getTargets().size());
        Assert.assertEquals("ws://host1:8080", ((WebSocketEndpoint) endpoint).getTargets().get(0).getUrl());
    }

    @Test
    public void testWebSocketEndpointFallbackToUrls() {
        WebSocketEndpoint.Config config = new WebSocketEndpoint.Config();
        config.setServiceId("some-service");
        config.setUrls(Arrays.asList("ws://fallback:8080"));

        WebSocketEndpoint endpoint = new WebSocketEndpoint(config);
        Assert.assertEquals(1, endpoint.getTargets().size());
        Assert.assertEquals("ws://fallback:8080", endpoint.getTargets().get(0).getUrl());
    }
}
