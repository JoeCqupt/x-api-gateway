package io.github.xinfra.lab.gateway.loadbalance;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LeastConnectionsTest {

    @Test
    public void testSingleTarget() {
        LeastConnectionsStrategy strategy = new LeastConnectionsStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        List<UpstreamTarget> targets = Collections.singletonList(t1);

        UpstreamTarget chosen = strategy.choose(targets, null);
        Assert.assertEquals("http://host1:8080", chosen.getUrl());
    }

    @Test
    public void testSelectsLeastConnections() {
        LeastConnectionsStrategy strategy = new LeastConnectionsStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080");
        UpstreamTarget t3 = new UpstreamTarget("http://host3:8080");

        t1.getActiveConnections().set(10);
        t2.getActiveConnections().set(2);
        t3.getActiveConnections().set(5);

        List<UpstreamTarget> targets = Arrays.asList(t1, t2, t3);
        UpstreamTarget chosen = strategy.choose(targets, null);
        Assert.assertEquals("http://host2:8080", chosen.getUrl());
    }

    @Test
    public void testAllUnhealthyReturnsNull() {
        LeastConnectionsStrategy strategy = new LeastConnectionsStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        t1.setHealthy(false);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080");
        t2.setHealthy(false);

        List<UpstreamTarget> targets = Arrays.asList(t1, t2);
        UpstreamTarget chosen = strategy.choose(targets, null);
        Assert.assertNull(chosen);
    }

    @Test
    public void testSkipsUnhealthy() {
        LeastConnectionsStrategy strategy = new LeastConnectionsStrategy();
        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080");
        t1.getActiveConnections().set(0);
        t1.setHealthy(false);

        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080");
        t2.getActiveConnections().set(5);

        List<UpstreamTarget> targets = Arrays.asList(t1, t2);
        UpstreamTarget chosen = strategy.choose(targets, null);
        Assert.assertEquals("http://host2:8080", chosen.getUrl());
    }

    @Test
    public void testFactoryRegistered() {
        LoadBalancerStrategy strategy = LoadBalancerStrategyFactory.get("LeastConnections");
        Assert.assertTrue(strategy instanceof LeastConnectionsStrategy);
    }
}
