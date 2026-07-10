package io.github.xinfra.lab.gateway.plugin;

import io.github.xinfra.lab.gateway.commons.AbstractConfigurable;
import io.github.xinfra.lab.gateway.config.ConfigSource;
import io.github.xinfra.lab.gateway.config.GatewayConfig;
import io.github.xinfra.lab.gateway.endpoint.EndpointFactory;
import io.github.xinfra.lab.gateway.endpoint.EndpointFactoryManager;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactory;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactoryManager;
import io.github.xinfra.lab.gateway.predicate.RoutePredicateFactory;
import io.github.xinfra.lab.gateway.predicate.RoutePredicateFactoryManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

public class DefaultPluginContextTest {

    @Test
    public void testRegisterFilterFactory() {
        DefaultPluginContext context = new DefaultPluginContext();
        GatewayFilterFactory factory = mock(GatewayFilterFactory.class);
        when(factory.getName()).thenReturn("TestFilter");

        context.registerFilter(factory);

        Assert.assertNotNull(GatewayFilterFactoryManager.INSTANCE.lookup("TestFilter"));
        Assert.assertEquals(factory, GatewayFilterFactoryManager.INSTANCE.lookup("TestFilter"));
    }

    @Test
    public void testRegisterPredicateFactory() {
        DefaultPluginContext context = new DefaultPluginContext();
        RoutePredicateFactory factory = mock(RoutePredicateFactory.class);
        when(factory.getName()).thenReturn("TestPredicate");

        context.registerPredicate(factory);

        Assert.assertNotNull(RoutePredicateFactoryManager.INSTANCE.lookup("TestPredicate"));
        Assert.assertEquals(factory, RoutePredicateFactoryManager.INSTANCE.lookup("TestPredicate"));
    }

    @Test
    public void testRegisterEndpointFactory() {
        DefaultPluginContext context = new DefaultPluginContext();
        EndpointFactory factory = mock(EndpointFactory.class);
        when(factory.getName()).thenReturn("TestEndpoint");

        context.registerEndpoint(factory);

        Assert.assertNotNull(EndpointFactoryManager.INSTANCE.lookup("TestEndpoint"));
        Assert.assertEquals(factory, EndpointFactoryManager.INSTANCE.lookup("TestEndpoint"));
    }

    @Test
    public void testMetricsCollectorNotNull() {
        DefaultPluginContext context = new DefaultPluginContext();
        Assert.assertNotNull(context.metricsCollector());
    }

    @Test
    public void testSerializerNotNull() {
        DefaultPluginContext context = new DefaultPluginContext();
        Assert.assertNotNull(context.serializer());
    }

    @Test
    public void testSchedulerNotNull() {
        DefaultPluginContext context = new DefaultPluginContext();
        Assert.assertNotNull(context.scheduler());
    }

    @Test
    public void testRegisterConfigSource() {
        DefaultPluginContext context = new DefaultPluginContext();
        ConfigSource source = new ConfigSource() {
            @Override
            public String name() { return "test-source"; }
            @Override
            public GatewayConfig load() { return null; }
            @Override
            public void watch(Consumer<GatewayConfig> listener) {}
            @Override
            public int order() { return 0; }
        };

        context.registerConfigSource(source);
        Assert.assertEquals(1, context.getConfigSources().size());
        Assert.assertEquals("test-source", context.getConfigSources().get(0).name());
    }

    @Test
    public void testRegisterAdminEndpoint() {
        DefaultPluginContext context = new DefaultPluginContext();
        Object handler = new Object();

        context.registerAdminEndpoint("/admin/custom", handler);
        Assert.assertEquals(1, context.getAdminEndpoints().size());
        Assert.assertSame(handler, context.getAdminEndpoints().get("/admin/custom"));
    }
}
