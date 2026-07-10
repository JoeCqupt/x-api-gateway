package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.junit.Assert;
import org.junit.Test;

import java.time.ZonedDateTime;

public class TimePredicateTest {

    @Test
    public void testAfterMatch() {
        TimeRoutePredicateFactory factory = new TimeRoutePredicateFactory();
        TimeRoutePredicateFactory.Config config = new TimeRoutePredicateFactory.Config();
        config.setAfter("2020-01-01T00:00:00Z");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);
        Assert.assertTrue(predicate.test(null));
    }

    @Test
    public void testAfterNoMatch() {
        TimeRoutePredicateFactory factory = new TimeRoutePredicateFactory();
        TimeRoutePredicateFactory.Config config = new TimeRoutePredicateFactory.Config();
        config.setAfter("2099-01-01T00:00:00Z");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);
        Assert.assertFalse(predicate.test(null));
    }

    @Test
    public void testBeforeMatch() {
        TimeRoutePredicateFactory factory = new TimeRoutePredicateFactory();
        TimeRoutePredicateFactory.Config config = new TimeRoutePredicateFactory.Config();
        config.setBefore("2099-12-31T23:59:59Z");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);
        Assert.assertTrue(predicate.test(null));
    }

    @Test
    public void testBeforeNoMatch() {
        TimeRoutePredicateFactory factory = new TimeRoutePredicateFactory();
        TimeRoutePredicateFactory.Config config = new TimeRoutePredicateFactory.Config();
        config.setBefore("2020-01-01T00:00:00Z");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);
        Assert.assertFalse(predicate.test(null));
    }

    @Test
    public void testWithinWindow() {
        TimeRoutePredicateFactory factory = new TimeRoutePredicateFactory();
        TimeRoutePredicateFactory.Config config = new TimeRoutePredicateFactory.Config();
        config.setAfter("2020-01-01T00:00:00Z");
        config.setBefore("2099-12-31T23:59:59Z");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);
        Assert.assertTrue(predicate.test(null));
    }

    @Test
    public void testOutsideWindow() {
        TimeRoutePredicateFactory factory = new TimeRoutePredicateFactory();
        TimeRoutePredicateFactory.Config config = new TimeRoutePredicateFactory.Config();
        config.setAfter("2099-01-01T00:00:00Z");
        config.setBefore("2099-12-31T23:59:59Z");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);
        Assert.assertFalse(predicate.test(null));
    }

    @Test
    public void testFactoryName() {
        TimeRoutePredicateFactory factory = new TimeRoutePredicateFactory();
        Assert.assertEquals("Time", factory.getName());
    }
}
