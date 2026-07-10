package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.commons.Ordered;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

public class ConfigurableGatewayFilterTest {

    @Test
    public void testGetOrder() {
        AtomicReference<String> ref = new AtomicReference<>("initial");
        ConfigurableGatewayFilter<String> filter = new ConfigurableGatewayFilter<>(
                ref, (e, c) -> Mono.empty(), -200, "TestFilter");
        Assert.assertEquals(-200, filter.getOrder());
    }

    @Test
    public void testGetFilterName() {
        AtomicReference<String> ref = new AtomicReference<>("v1");
        ConfigurableGatewayFilter<String> filter = new ConfigurableGatewayFilter<>(
                ref, (e, c) -> Mono.empty(), 100, "MyFilter");
        Assert.assertEquals("MyFilter", filter.getFilterName());
    }

    @Test
    public void testGetConfig() {
        AtomicReference<String> ref = new AtomicReference<>("config-value");
        ConfigurableGatewayFilter<String> filter = new ConfigurableGatewayFilter<>(
                ref, (e, c) -> Mono.empty(), 0, "F");
        Assert.assertEquals("config-value", filter.getConfig());
    }

    @Test
    public void testUpdateConfig() {
        AtomicReference<String> ref = new AtomicReference<>("old");
        ConfigurableGatewayFilter<String> filter = new ConfigurableGatewayFilter<>(
                ref, (e, c) -> Mono.empty(), 0, "F");

        filter.updateConfig("new");
        Assert.assertEquals("new", filter.getConfig());
        Assert.assertEquals("new", ref.get());
    }

    @Test
    public void testImplementsOrdered() {
        AtomicReference<String> ref = new AtomicReference<>("");
        ConfigurableGatewayFilter<String> filter = new ConfigurableGatewayFilter<>(
                ref, (e, c) -> Mono.empty(), -400, "F");
        Assert.assertTrue(filter instanceof Ordered);
    }

    @Test
    public void testGetDelegate() {
        GatewayFilter inner = (e, c) -> Mono.empty();
        AtomicReference<String> ref = new AtomicReference<>("");
        ConfigurableGatewayFilter<String> filter = new ConfigurableGatewayFilter<>(
                ref, inner, 0, "F");
        Assert.assertSame(inner, filter.getDelegate());
    }

    @Test
    public void testRateLimitFactoryReturnsConfigurable() {
        RateLimitGatewayFilterFactory factory = new RateLimitGatewayFilterFactory();
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setReplenishRate(100);
        config.setBurstCapacity(200);

        GatewayFilter filter = factory.apply(config);
        Assert.assertTrue("Should be ConfigurableGatewayFilter", filter instanceof ConfigurableGatewayFilter);

        ConfigurableGatewayFilter<?> cf = (ConfigurableGatewayFilter<?>) filter;
        Assert.assertEquals("RateLimit", cf.getFilterName());
        Assert.assertEquals(-200, cf.getOrder());
    }

    @Test
    public void testIpFilterFactoryReturnsConfigurable() {
        IpFilterGatewayFilterFactory factory = new IpFilterGatewayFilterFactory();
        IpFilterGatewayFilterFactory.Config config = new IpFilterGatewayFilterFactory.Config();

        GatewayFilter filter = factory.apply(config);
        Assert.assertTrue("Should be ConfigurableGatewayFilter", filter instanceof ConfigurableGatewayFilter);

        ConfigurableGatewayFilter<?> cf = (ConfigurableGatewayFilter<?>) filter;
        Assert.assertEquals("IpFilter", cf.getFilterName());
        Assert.assertEquals(-400, cf.getOrder());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRateLimitConfigHotUpdate() {
        RateLimitGatewayFilterFactory factory = new RateLimitGatewayFilterFactory();
        RateLimitGatewayFilterFactory.Config config = new RateLimitGatewayFilterFactory.Config();
        config.setReplenishRate(100);
        config.setBurstCapacity(200);

        GatewayFilter filter = factory.apply(config);
        ConfigurableGatewayFilter<RateLimitGatewayFilterFactory.Config> cf =
                (ConfigurableGatewayFilter<RateLimitGatewayFilterFactory.Config>) filter;

        Assert.assertEquals(100, cf.getConfig().getReplenishRate());
        Assert.assertEquals(200, cf.getConfig().getBurstCapacity());

        RateLimitGatewayFilterFactory.Config newConfig = new RateLimitGatewayFilterFactory.Config();
        newConfig.setReplenishRate(50);
        newConfig.setBurstCapacity(100);
        cf.updateConfig(newConfig);

        Assert.assertEquals(50, cf.getConfig().getReplenishRate());
        Assert.assertEquals(100, cf.getConfig().getBurstCapacity());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIpFilterConfigHotUpdate() {
        IpFilterGatewayFilterFactory factory = new IpFilterGatewayFilterFactory();
        IpFilterGatewayFilterFactory.Config config = new IpFilterGatewayFilterFactory.Config();

        GatewayFilter filter = factory.apply(config);
        ConfigurableGatewayFilter<IpFilterGatewayFilterFactory.Config> cf =
                (ConfigurableGatewayFilter<IpFilterGatewayFilterFactory.Config>) filter;

        Assert.assertTrue(cf.getConfig().getWhitelist().isEmpty());

        IpFilterGatewayFilterFactory.Config newConfig = new IpFilterGatewayFilterFactory.Config();
        newConfig.setWhitelist(java.util.Arrays.asList("10.0.0.0/8", "192.168.1.0/24"));
        cf.updateConfig(newConfig);

        Assert.assertEquals(2, cf.getConfig().getWhitelist().size());
        Assert.assertEquals("10.0.0.0/8", cf.getConfig().getWhitelist().get(0));
    }
}
