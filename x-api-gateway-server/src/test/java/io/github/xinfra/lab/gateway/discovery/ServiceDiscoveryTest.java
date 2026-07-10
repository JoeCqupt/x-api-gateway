package io.github.xinfra.lab.gateway.discovery;

import io.github.xinfra.lab.gateway.endpoint.EndpointFactoryManager;
import io.github.xinfra.lab.gateway.endpoint.HttpEndpoint;
import io.github.xinfra.lab.gateway.endpoint.HttpEndpointFactory;
import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ServiceDiscoveryTest {

    @Test
    public void testStaticDiscoveryRegisterAndGet() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst1 = new ServiceInstance("svc-0", "host1", 8080);
        ServiceInstance inst2 = new ServiceInstance("svc-1", "host2", 8080);

        discovery.registerInstances("my-service", Arrays.asList(inst1, inst2));

        List<ServiceInstance> instances = discovery.getInstances("my-service");
        Assert.assertEquals(2, instances.size());
        Assert.assertEquals("host1", instances.get(0).getHost());
        Assert.assertEquals("host2", instances.get(1).getHost());
    }

    @Test
    public void testStaticDiscoveryUnknownService() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        List<ServiceInstance> instances = discovery.getInstances("unknown");
        Assert.assertTrue(instances.isEmpty());
    }

    @Test
    public void testStaticDiscoveryFromUrls() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        discovery.registerFromUrls("url-service",
                Arrays.asList("http://host1:8080", "http://host2:9090"));

        List<ServiceInstance> instances = discovery.getInstances("url-service");
        Assert.assertEquals(2, instances.size());
        Assert.assertEquals("host1", instances.get(0).getHost());
        Assert.assertEquals(8080, instances.get(0).getPort());
        Assert.assertEquals("host2", instances.get(1).getHost());
        Assert.assertEquals(9090, instances.get(1).getPort());
    }

    @Test
    public void testStaticDiscoverySubscribe() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        AtomicReference<List<ServiceInstance>> notified = new AtomicReference<>();
        discovery.subscribe("watch-service", notified::set);

        ServiceInstance inst = new ServiceInstance("w-0", "host1", 8080);
        discovery.registerInstances("watch-service", Arrays.asList(inst));

        Assert.assertNotNull(notified.get());
        Assert.assertEquals(1, notified.get().size());
    }

    @Test
    public void testServiceInstanceAddress() {
        ServiceInstance inst = new ServiceInstance("id-1", "localhost", 8080);
        Assert.assertEquals("localhost:8080", inst.getAddress());
    }

    @Test
    public void testServiceInstanceHealthy() {
        ServiceInstance inst = new ServiceInstance("id-1", "localhost", 8080);
        Assert.assertTrue(inst.isHealthy());
        inst.setHealthy(false);
        Assert.assertFalse(inst.isHealthy());
    }

    @Test
    public void testStaticDiscoveryStop() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst = new ServiceInstance("s-0", "host1", 8080);
        discovery.registerInstances("svc", Arrays.asList(inst));
        Assert.assertEquals(1, discovery.getInstances("svc").size());

        discovery.stop();
        Assert.assertTrue(discovery.getInstances("svc").isEmpty());
    }

    @Test
    public void testEndpointFactoryManagerSetServiceDiscovery() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst = new ServiceInstance("e-0", "host1", 8080);
        discovery.registerInstances("endpoint-svc", Arrays.asList(inst));

        EndpointFactoryManager manager = new EndpointFactoryManager();
        manager.setServiceDiscovery(discovery);

        HttpEndpointFactory factory = (HttpEndpointFactory) manager.lookup("Http");
        Assert.assertNotNull(factory);

        HttpEndpoint.Config config = new HttpEndpoint.Config();
        config.setServiceId("endpoint-svc");

        HttpEndpoint endpoint = (HttpEndpoint) factory.apply(config);
        List<UpstreamTarget> targets = endpoint.getTargets();
        Assert.assertEquals(1, targets.size());
        Assert.assertEquals("http://host1:8080", targets.get(0).getUrl());
    }

    @Test
    public void testDiscoveryDrivenEndpointDynamicUpdate() {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        ServiceInstance inst1 = new ServiceInstance("d-0", "host1", 8080);
        discovery.registerInstances("dyn-svc", Arrays.asList(inst1));

        HttpEndpoint.Config config = new HttpEndpoint.Config();
        config.setServiceId("dyn-svc");

        HttpEndpoint endpoint = new HttpEndpoint(config, discovery);
        Assert.assertEquals(1, endpoint.getTargets().size());

        ServiceInstance inst2 = new ServiceInstance("d-1", "host2", 9090);
        ServiceInstance inst3 = new ServiceInstance("d-2", "host3", 7070);
        discovery.registerInstances("dyn-svc", Arrays.asList(inst2, inst3));

        List<UpstreamTarget> targets = endpoint.getTargets();
        Assert.assertEquals(2, targets.size());
    }
}
