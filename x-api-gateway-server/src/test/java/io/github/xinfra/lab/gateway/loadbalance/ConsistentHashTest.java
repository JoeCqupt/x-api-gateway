package io.github.xinfra.lab.gateway.loadbalance;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class ConsistentHashTest {

    private ServerWebExchange mockExchangeWithIp(String ip) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        try {
            InetAddress addr = InetAddress.getByName(ip);
            when(request.remoteAddress()).thenReturn(new InetSocketAddress(addr, 12345));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testSameKeySameTarget() {
        ConsistentHashStrategy strategy = new ConsistentHashStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080");
        UpstreamTarget t3 = new UpstreamTarget("http://host3:8080");
        List<UpstreamTarget> targets = Arrays.asList(t1, t2, t3);

        ServerWebExchange exchange = mockExchangeWithIp("192.168.1.100");
        UpstreamTarget first = strategy.choose(targets, exchange);
        Assert.assertNotNull(first);

        for (int i = 0; i < 100; i++) {
            ServerWebExchange ex = mockExchangeWithIp("192.168.1.100");
            UpstreamTarget chosen = strategy.choose(targets, ex);
            Assert.assertSame(first, chosen);
        }
    }

    @Test
    public void testDistribution() {
        ConsistentHashStrategy strategy = new ConsistentHashStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080");
        UpstreamTarget t3 = new UpstreamTarget("http://host3:8080");
        List<UpstreamTarget> targets = Arrays.asList(t1, t2, t3);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 300; i++) {
            ServerWebExchange ex = mockExchangeWithIp("10.0." + (i / 256) + "." + (i % 256));
            UpstreamTarget chosen = strategy.choose(targets, ex);
            counts.merge(chosen.getUrl(), 1, Integer::sum);
        }

        Assert.assertEquals(3, counts.size());
        for (int count : counts.values()) {
            Assert.assertTrue("Each target should get some traffic, got " + count, count > 0);
        }
    }

    @Test
    public void testNodeFailureRehash() {
        ConsistentHashStrategy strategy = new ConsistentHashStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080");
        UpstreamTarget t3 = new UpstreamTarget("http://host3:8080");
        List<UpstreamTarget> allTargets = Arrays.asList(t1, t2, t3);

        ServerWebExchange ex = mockExchangeWithIp("192.168.1.50");
        UpstreamTarget before = strategy.choose(allTargets, ex);
        Assert.assertNotNull(before);

        t2.setHealthy(false);
        List<UpstreamTarget> withoutT2 = Arrays.asList(t1, t3);

        ConsistentHashStrategy strategy2 = new ConsistentHashStrategy();
        ServerWebExchange ex2 = mockExchangeWithIp("192.168.1.50");
        UpstreamTarget after = strategy2.choose(withoutT2, ex2);
        Assert.assertNotNull(after);
    }

    @Test
    public void testSingleTarget() {
        ConsistentHashStrategy strategy = new ConsistentHashStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        List<UpstreamTarget> targets = Arrays.asList(t1);

        ServerWebExchange ex = mockExchangeWithIp("192.168.1.1");
        UpstreamTarget chosen = strategy.choose(targets, ex);
        Assert.assertEquals("http://host1:8080", chosen.getUrl());
    }

    @Test
    public void testHashKeyFromHeader() {
        ConsistentHashStrategy strategy = new ConsistentHashStrategy(160, "X-Session-Id");
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080");
        List<UpstreamTarget> targets = Arrays.asList(t1, t2);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("X-Session-Id", "session-abc");
        when(request.requestHeaders()).thenReturn(headers);
        when(request.method()).thenReturn(HttpMethod.GET);
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        UpstreamTarget first = strategy.choose(targets, exchange);
        Assert.assertNotNull(first);

        HttpServerRequest request2 = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers2 = new DefaultHttpHeaders();
        headers2.add("X-Session-Id", "session-abc");
        when(request2.requestHeaders()).thenReturn(headers2);
        when(request2.method()).thenReturn(HttpMethod.GET);
        ServerWebExchange exchange2 = new ServerWebExchange(request2, null);

        UpstreamTarget second = strategy.choose(targets, exchange2);
        Assert.assertSame(first, second);
    }

    @Test
    public void testFactoryRegistered() {
        LoadBalancerStrategy strategy = LoadBalancerStrategyFactory.get("ConsistentHash");
        Assert.assertTrue(strategy instanceof ConsistentHashStrategy);
    }
}
