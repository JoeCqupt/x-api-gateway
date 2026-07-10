package io.github.xinfra.lab.gateway.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.config.WritableConfigSource;
import io.github.xinfra.lab.gateway.route.RouteDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NacosConfigSourceTest {

    private ConfigService mockConfigService;
    private NacosConfigSource source;

    @Before
    public void setup() {
        mockConfigService = mock(ConfigService.class);
        source = new NacosConfigSource(mockConfigService, "x-api-gateway", "DEFAULT_GROUP");
    }

    @Test
    public void testName() {
        Assert.assertEquals("nacos", source.name());
    }

    @Test
    public void testOrder() {
        Assert.assertEquals(50, source.order());
    }

    @Test
    public void testIsWritableConfigSource() {
        Assert.assertTrue(source instanceof WritableConfigSource);
    }

    @Test
    public void testGetDataIdAndGroup() {
        Assert.assertEquals("x-api-gateway", source.getDataId());
        Assert.assertEquals("DEFAULT_GROUP", source.getGroup());
    }

    @Test
    public void testLoad() throws NacosException {
        String yaml = "routes:\n- id: test-route\n  order: 1\n";
        when(mockConfigService.getConfig("x-api-gateway", "DEFAULT_GROUP", 5000))
                .thenReturn(yaml);

        GatewayConfig config = source.load();
        Assert.assertNotNull(config);
        Assert.assertNotNull(config.getRoutes());
        Assert.assertEquals(1, config.getRoutes().size());
        Assert.assertEquals("test-route", config.getRoutes().get(0).getId());
    }

    @Test
    public void testLoadEmptyConfig() throws NacosException {
        when(mockConfigService.getConfig("x-api-gateway", "DEFAULT_GROUP", 5000))
                .thenReturn("");

        GatewayConfig config = source.load();
        Assert.assertNotNull(config);
    }

    @Test
    public void testLoadNullConfig() throws NacosException {
        when(mockConfigService.getConfig("x-api-gateway", "DEFAULT_GROUP", 5000))
                .thenReturn(null);

        GatewayConfig config = source.load();
        Assert.assertNotNull(config);
    }

    @Test
    public void testSave() throws NacosException {
        when(mockConfigService.publishConfig(eq("x-api-gateway"), eq("DEFAULT_GROUP"), anyString()))
                .thenReturn(true);

        GatewayConfig config = new GatewayConfig();
        RouteDefinition rd = new RouteDefinition();
        rd.setId("saved-route");
        config.setRoutes(Collections.singletonList(rd));

        source.save(config);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockConfigService).publishConfig(eq("x-api-gateway"), eq("DEFAULT_GROUP"),
                contentCaptor.capture());

        String content = contentCaptor.getValue();
        Assert.assertTrue(content.contains("saved-route"));
    }

    @Test
    public void testWatch() throws NacosException {
        AtomicReference<GatewayConfig> received = new AtomicReference<>();
        source.watch(received::set);

        verify(mockConfigService).addListener(eq("x-api-gateway"), eq("DEFAULT_GROUP"), any(Listener.class));
    }

    @Test
    public void testWatchListenerReceivesConfig() throws NacosException {
        AtomicReference<GatewayConfig> received = new AtomicReference<>();

        ArgumentCaptor<Listener> listenerCaptor = ArgumentCaptor.forClass(Listener.class);
        source.watch(received::set);
        verify(mockConfigService).addListener(eq("x-api-gateway"), eq("DEFAULT_GROUP"),
                listenerCaptor.capture());

        Listener nacosListener = listenerCaptor.getValue();
        nacosListener.receiveConfigInfo("routes:\n- id: watched-route\n");

        Assert.assertNotNull(received.get());
        Assert.assertNotNull(received.get().getRoutes());
        Assert.assertEquals("watched-route", received.get().getRoutes().get(0).getId());
    }

    @Test
    public void testFactoryCreate() {
        GatewayConfig.NacosConfig nacosConfig = new GatewayConfig.NacosConfig();
        nacosConfig.setServerAddr("127.0.0.1:8848");
        nacosConfig.setDataId("my-gateway");
        nacosConfig.setGroup("MY_GROUP");

        try {
            NacosConfigSource created = NacosConfigSourceFactory.create(nacosConfig);
            Assert.assertEquals("my-gateway", created.getDataId());
            Assert.assertEquals("MY_GROUP", created.getGroup());
        } catch (RuntimeException e) {
            // may fail to connect to nacos in test environment, that's ok
            Assert.assertTrue(e.getMessage().contains("Nacos") || e.getMessage().contains("nacos"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryCreateNoServerAddr() {
        GatewayConfig.NacosConfig nacosConfig = new GatewayConfig.NacosConfig();
        NacosConfigSourceFactory.create(nacosConfig);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryCreateNullConfig() {
        NacosConfigSourceFactory.create(null);
    }
}
