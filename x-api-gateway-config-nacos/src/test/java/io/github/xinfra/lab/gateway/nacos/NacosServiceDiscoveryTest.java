package io.github.xinfra.lab.gateway.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.discovery.ServiceInstance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NacosServiceDiscoveryTest {

    private NamingService mockNamingService;
    private NacosServiceDiscovery discovery;

    @Before
    public void setup() {
        mockNamingService = mock(NamingService.class);
        discovery = new NacosServiceDiscovery(mockNamingService);
    }

    @Test
    public void testGetInstances() throws NacosException {
        Instance inst1 = new Instance();
        inst1.setInstanceId("inst-1");
        inst1.setIp("192.168.1.1");
        inst1.setPort(8080);
        inst1.setHealthy(true);

        Instance inst2 = new Instance();
        inst2.setInstanceId("inst-2");
        inst2.setIp("192.168.1.2");
        inst2.setPort(9090);
        inst2.setHealthy(true);

        when(mockNamingService.selectInstances("my-service", true))
                .thenReturn(Arrays.asList(inst1, inst2));

        List<ServiceInstance> instances = discovery.getInstances("my-service");
        Assert.assertEquals(2, instances.size());
        Assert.assertEquals("192.168.1.1", instances.get(0).getHost());
        Assert.assertEquals(8080, instances.get(0).getPort());
        Assert.assertEquals("192.168.1.2", instances.get(1).getHost());
        Assert.assertEquals(9090, instances.get(1).getPort());
    }

    @Test
    public void testGetInstancesEmpty() throws NacosException {
        when(mockNamingService.selectInstances("empty-service", true))
                .thenReturn(Collections.<Instance>emptyList());

        List<ServiceInstance> instances = discovery.getInstances("empty-service");
        Assert.assertTrue(instances.isEmpty());
    }

    @Test
    public void testGetInstancesOnError() throws NacosException {
        when(mockNamingService.selectInstances("error-service", true))
                .thenThrow(new NacosException(500, "test error"));

        List<ServiceInstance> instances = discovery.getInstances("error-service");
        Assert.assertTrue(instances.isEmpty());
    }

    @Test
    public void testSubscribe() throws NacosException {
        AtomicReference<List<ServiceInstance>> received = new AtomicReference<>();
        discovery.subscribe("sub-service", received::set);

        verify(mockNamingService).subscribe(eq("sub-service"), any(EventListener.class));
    }

    @Test
    public void testSubscribeReceivesUpdate() throws NacosException {
        AtomicReference<List<ServiceInstance>> received = new AtomicReference<>();

        ArgumentCaptor<EventListener> listenerCaptor = ArgumentCaptor.forClass(EventListener.class);
        discovery.subscribe("sub-service", received::set);
        verify(mockNamingService).subscribe(eq("sub-service"), listenerCaptor.capture());

        Instance inst = new Instance();
        inst.setInstanceId("new-inst");
        inst.setIp("10.0.0.1");
        inst.setPort(8080);
        inst.setHealthy(true);

        NamingEvent event = new NamingEvent("sub-service", Arrays.asList(inst));
        listenerCaptor.getValue().onEvent(event);

        Assert.assertNotNull(received.get());
        Assert.assertEquals(1, received.get().size());
        Assert.assertEquals("10.0.0.1", received.get().get(0).getHost());
        Assert.assertEquals(8080, received.get().get(0).getPort());
    }

    @Test
    public void testGetInstancesPreservesMetadata() throws NacosException {
        Instance inst = new Instance();
        inst.setInstanceId("meta-inst");
        inst.setIp("192.168.1.1");
        inst.setPort(8080);
        inst.setHealthy(true);
        inst.getMetadata().put("version", "v2");
        inst.getMetadata().put("env", "prod");

        when(mockNamingService.selectInstances("meta-service", true))
                .thenReturn(Arrays.asList(inst));

        List<ServiceInstance> instances = discovery.getInstances("meta-service");
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("v2", instances.get(0).getMetadata().get("version"));
        Assert.assertEquals("prod", instances.get(0).getMetadata().get("env"));
    }

    @Test
    public void testStop() throws NacosException {
        discovery.stop();
        verify(mockNamingService).shutDown();
    }

    @Test
    public void testFactoryCreate() {
        GatewayConfig.NacosConfig nacosConfig = new GatewayConfig.NacosConfig();
        nacosConfig.setServerAddr("127.0.0.1:8848");

        try {
            NacosServiceDiscovery created = NacosServiceDiscoveryFactory.create(nacosConfig);
            Assert.assertNotNull(created);
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Nacos") || e.getMessage().contains("nacos"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryCreateNoServerAddr() {
        GatewayConfig.NacosConfig nacosConfig = new GatewayConfig.NacosConfig();
        NacosServiceDiscoveryFactory.create(nacosConfig);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryCreateNullConfig() {
        NacosServiceDiscoveryFactory.create(null);
    }
}
