package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.junit.Assert;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IPHashTest {

    @Test
    public void testSameIpSameTarget() throws Exception {
        IPHashStrategy strategy = new IPHashStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 1);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080", 1);
        UpstreamTarget t3 = new UpstreamTarget("http://host3:8080", 1);
        List<UpstreamTarget> targets = Arrays.asList(t1, t2, t3);

        ServerWebExchange exchange = mockExchangeWithIp("192.168.1.100");

        UpstreamTarget first = strategy.choose(targets, exchange);
        for (int i = 0; i < 10; i++) {
            UpstreamTarget chosen = strategy.choose(targets, exchange);
            Assert.assertSame(first, chosen);
        }
    }

    @Test
    public void testDistribution() throws Exception {
        IPHashStrategy strategy = new IPHashStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 1);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080", 1);
        UpstreamTarget t3 = new UpstreamTarget("http://host3:8080", 1);
        List<UpstreamTarget> targets = Arrays.asList(t1, t2, t3);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 300; i++) {
            String ip = "10.0." + (i / 256) + "." + (i % 256);
            ServerWebExchange exchange = mockExchangeWithIp(ip);
            UpstreamTarget chosen = strategy.choose(targets, exchange);
            counts.merge(chosen.getUrl(), 1, Integer::sum);
        }

        for (int count : counts.values()) {
            Assert.assertTrue("Each target should get at least some requests", count > 10);
        }
    }

    @Test
    public void testSkipsUnhealthy() throws Exception {
        IPHashStrategy strategy = new IPHashStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 1);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080", 1);
        t1.setHealthy(false);
        List<UpstreamTarget> targets = Arrays.asList(t1, t2);

        ServerWebExchange exchange = mockExchangeWithIp("192.168.1.1");
        UpstreamTarget chosen = strategy.choose(targets, exchange);
        Assert.assertSame(t2, chosen);
    }

    @Test
    public void testAllUnhealthy() throws Exception {
        IPHashStrategy strategy = new IPHashStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 1);
        t1.setHealthy(false);
        List<UpstreamTarget> targets = Arrays.asList(t1);

        ServerWebExchange exchange = mockExchangeWithIp("192.168.1.1");
        Assert.assertNull(strategy.choose(targets, exchange));
    }

    @Test
    public void testFactoryRegistered() {
        LoadBalancerStrategy strategy = LoadBalancerStrategyFactory.get("IPHash");
        Assert.assertNotNull(strategy);
        Assert.assertTrue(strategy instanceof IPHashStrategy);
    }

    @Test
    public void testNullExchange() {
        IPHashStrategy strategy = new IPHashStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 1);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080", 1);
        List<UpstreamTarget> targets = Arrays.asList(t1, t2);

        UpstreamTarget chosen = strategy.choose(targets, null);
        Assert.assertNotNull(chosen);
    }

    private ServerWebExchange mockExchangeWithIp(String ip) throws Exception {
        HttpServerRequest request = mock(HttpServerRequest.class);
        InetAddress addr = InetAddress.getByName(ip);
        when(request.remoteAddress()).thenReturn(new InetSocketAddress(addr, 12345));
        return new ServerWebExchange(request, null);
    }
}
