package io.github.xinfra.lab.gateway.loadbalance;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadBalancerTest {

    @Test
    public void testWeightedRoundRobin() {
        WeightedRoundRobinStrategy strategy = new WeightedRoundRobinStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 5);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080", 3);
        UpstreamTarget t3 = new UpstreamTarget("http://host3:8080", 2);
        List<UpstreamTarget> targets = Arrays.asList(t1, t2, t3);

        Map<String, Integer> counts = new HashMap<>();
        int total = 1000;
        for (int i = 0; i < total; i++) {
            UpstreamTarget chosen = strategy.choose(targets, null);
            counts.merge(chosen.getUrl(), 1, Integer::sum);
        }

        // weight ratio should be approximately 5:3:2
        Assert.assertEquals(500, counts.get("http://host1:8080").intValue());
        Assert.assertEquals(300, counts.get("http://host2:8080").intValue());
        Assert.assertEquals(200, counts.get("http://host3:8080").intValue());
    }

    @Test
    public void testWeightedRoundRobinSkipsUnhealthy() {
        WeightedRoundRobinStrategy strategy = new WeightedRoundRobinStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 1);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080", 1);
        t2.setHealthy(false);
        List<UpstreamTarget> targets = Arrays.asList(t1, t2);

        for (int i = 0; i < 10; i++) {
            UpstreamTarget chosen = strategy.choose(targets, null);
            Assert.assertEquals("http://host1:8080", chosen.getUrl());
        }
    }

    @Test
    public void testWeightedRoundRobinAllUnhealthy() {
        WeightedRoundRobinStrategy strategy = new WeightedRoundRobinStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 1);
        t1.setHealthy(false);
        List<UpstreamTarget> targets = Arrays.asList(t1);

        UpstreamTarget chosen = strategy.choose(targets, null);
        Assert.assertNull(chosen);
    }

    @Test
    public void testRandomStrategy() {
        RandomStrategy strategy = new RandomStrategy();

        UpstreamTarget t1 = new UpstreamTarget("http://host1:8080", 80);
        UpstreamTarget t2 = new UpstreamTarget("http://host2:8080", 20);
        List<UpstreamTarget> targets = Arrays.asList(t1, t2);

        Map<String, Integer> counts = new HashMap<>();
        int total = 10000;
        for (int i = 0; i < total; i++) {
            UpstreamTarget chosen = strategy.choose(targets, null);
            counts.merge(chosen.getUrl(), 1, Integer::sum);
        }

        // with 80:20 weight ratio, host1 should get ~80% of traffic
        double host1Ratio = counts.get("http://host1:8080") / (double) total;
        Assert.assertTrue("host1 ratio should be ~0.8, was " + host1Ratio,
                host1Ratio > 0.7 && host1Ratio < 0.9);
    }

    @Test
    public void testTotalRequestsTracking() {
        UpstreamTarget target = new UpstreamTarget("http://host1:8080", 1);
        Assert.assertEquals(0, target.getTotalRequests().get());

        target.getTotalRequests().incrementAndGet();
        target.getTotalRequests().incrementAndGet();
        target.getTotalRequests().incrementAndGet();
        Assert.assertEquals(3, target.getTotalRequests().get());
    }

    @Test
    public void testLoadBalancerStrategyFactory() {
        LoadBalancerStrategy wrr = LoadBalancerStrategyFactory.get("WeightedRoundRobin");
        Assert.assertTrue(wrr instanceof WeightedRoundRobinStrategy);

        LoadBalancerStrategy random = LoadBalancerStrategyFactory.get("Random");
        Assert.assertTrue(random instanceof RandomStrategy);

        // default
        LoadBalancerStrategy defaultStrategy = LoadBalancerStrategyFactory.get(null);
        Assert.assertTrue(defaultStrategy instanceof WeightedRoundRobinStrategy);

        // unknown falls back to default
        LoadBalancerStrategy unknown = LoadBalancerStrategyFactory.get("Unknown");
        Assert.assertTrue(unknown instanceof WeightedRoundRobinStrategy);
    }
}
