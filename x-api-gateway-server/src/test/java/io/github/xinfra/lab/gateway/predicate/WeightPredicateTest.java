package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WeightPredicateTest {

    @Before
    public void setup() {
        WeightRoutePredicateFactory.resetGroups();
    }

    @After
    public void teardown() {
        WeightRoutePredicateFactory.resetGroups();
    }

    @Test
    public void testWeightDistribution80_20() {
        WeightRoutePredicateFactory factory = new WeightRoutePredicateFactory();

        WeightRoutePredicateFactory.Config config1 = new WeightRoutePredicateFactory.Config();
        config1.setGroup("test-group");
        config1.setWeight(80);

        WeightRoutePredicateFactory.Config config2 = new WeightRoutePredicateFactory.Config();
        config2.setGroup("test-group");
        config2.setWeight(20);

        RoutePredicate<ServerWebExchange> predicate1 = factory.apply(config1);
        RoutePredicate<ServerWebExchange> predicate2 = factory.apply(config2);

        ServerWebExchange exchange = new ServerWebExchange(null, null);

        int route1Count = 0;
        int route2Count = 0;
        int total = 1000;

        for (int i = 0; i < total; i++) {
            if (predicate1.test(exchange)) {
                route1Count++;
            }
            if (predicate2.test(exchange)) {
                route2Count++;
            }
        }

        Assert.assertEquals(800, route1Count);
        Assert.assertEquals(200, route2Count);
    }

    @Test
    public void testWeightDistribution50_50() {
        WeightRoutePredicateFactory factory = new WeightRoutePredicateFactory();

        WeightRoutePredicateFactory.Config config1 = new WeightRoutePredicateFactory.Config();
        config1.setGroup("equal-group");
        config1.setWeight(50);

        WeightRoutePredicateFactory.Config config2 = new WeightRoutePredicateFactory.Config();
        config2.setGroup("equal-group");
        config2.setWeight(50);

        RoutePredicate<ServerWebExchange> predicate1 = factory.apply(config1);
        RoutePredicate<ServerWebExchange> predicate2 = factory.apply(config2);

        ServerWebExchange exchange = new ServerWebExchange(null, null);

        int route1Count = 0;
        int route2Count = 0;
        int total = 100;

        for (int i = 0; i < total; i++) {
            if (predicate1.test(exchange)) route1Count++;
            if (predicate2.test(exchange)) route2Count++;
        }

        Assert.assertEquals(50, route1Count);
        Assert.assertEquals(50, route2Count);
    }

    @Test
    public void testMutuallyExclusive() {
        WeightRoutePredicateFactory factory = new WeightRoutePredicateFactory();

        WeightRoutePredicateFactory.Config config1 = new WeightRoutePredicateFactory.Config();
        config1.setGroup("exclusive-group");
        config1.setWeight(70);

        WeightRoutePredicateFactory.Config config2 = new WeightRoutePredicateFactory.Config();
        config2.setGroup("exclusive-group");
        config2.setWeight(30);

        RoutePredicate<ServerWebExchange> predicate1 = factory.apply(config1);
        RoutePredicate<ServerWebExchange> predicate2 = factory.apply(config2);

        ServerWebExchange exchange = new ServerWebExchange(null, null);

        for (int i = 0; i < 100; i++) {
            boolean match1 = predicate1.test(exchange);
            boolean match2 = predicate2.test(exchange);
            Assert.assertNotEquals("Exactly one predicate should match", match1, match2);
        }
    }

    @Test
    public void testFactoryName() {
        WeightRoutePredicateFactory factory = new WeightRoutePredicateFactory();
        Assert.assertEquals("Weight", factory.getName());
    }

    @Test
    public void testConfigClass() {
        WeightRoutePredicateFactory factory = new WeightRoutePredicateFactory();
        Assert.assertEquals(WeightRoutePredicateFactory.Config.class, factory.getConfigClass());
    }
}
