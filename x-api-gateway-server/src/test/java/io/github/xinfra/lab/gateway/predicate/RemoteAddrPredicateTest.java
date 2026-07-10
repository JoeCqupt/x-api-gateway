package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.junit.Assert;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteAddrPredicateTest {

    @Test
    public void testCidrMatch() throws Exception {
        RemoteAddrRoutePredicateFactory factory = new RemoteAddrRoutePredicateFactory();
        RemoteAddrRoutePredicateFactory.Config config = new RemoteAddrRoutePredicateFactory.Config();
        config.setSources(Collections.singletonList("192.168.0.0/16"));

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchangeWithIp("192.168.1.100");
        Assert.assertTrue(predicate.test(exchange));
    }

    @Test
    public void testCidrNoMatch() throws Exception {
        RemoteAddrRoutePredicateFactory factory = new RemoteAddrRoutePredicateFactory();
        RemoteAddrRoutePredicateFactory.Config config = new RemoteAddrRoutePredicateFactory.Config();
        config.setSources(Collections.singletonList("192.168.0.0/16"));

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchangeWithIp("10.0.0.1");
        Assert.assertFalse(predicate.test(exchange));
    }

    @Test
    public void testMultipleCidrRanges() throws Exception {
        RemoteAddrRoutePredicateFactory factory = new RemoteAddrRoutePredicateFactory();
        RemoteAddrRoutePredicateFactory.Config config = new RemoteAddrRoutePredicateFactory.Config();
        config.setSources(Arrays.asList("192.168.0.0/16", "10.0.0.0/8"));

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        Assert.assertTrue(predicate.test(mockExchangeWithIp("192.168.1.1")));
        Assert.assertTrue(predicate.test(mockExchangeWithIp("10.50.0.1")));
        Assert.assertFalse(predicate.test(mockExchangeWithIp("172.16.0.1")));
    }

    @Test
    public void testExactIp() throws Exception {
        RemoteAddrRoutePredicateFactory factory = new RemoteAddrRoutePredicateFactory();
        RemoteAddrRoutePredicateFactory.Config config = new RemoteAddrRoutePredicateFactory.Config();
        config.setSources(Collections.singletonList("10.0.0.1/32"));

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        Assert.assertTrue(predicate.test(mockExchangeWithIp("10.0.0.1")));
        Assert.assertFalse(predicate.test(mockExchangeWithIp("10.0.0.2")));
    }

    @Test
    public void testNullRemoteAddress() {
        RemoteAddrRoutePredicateFactory factory = new RemoteAddrRoutePredicateFactory();
        RemoteAddrRoutePredicateFactory.Config config = new RemoteAddrRoutePredicateFactory.Config();
        config.setSources(Collections.singletonList("192.168.0.0/16"));

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.remoteAddress()).thenReturn(null);
        ServerWebExchange exchange = new ServerWebExchange(request, null);
        Assert.assertFalse(predicate.test(exchange));
    }

    @Test
    public void testFactoryName() {
        RemoteAddrRoutePredicateFactory factory = new RemoteAddrRoutePredicateFactory();
        Assert.assertEquals("RemoteAddr", factory.getName());
    }

    private ServerWebExchange mockExchangeWithIp(String ip) throws Exception {
        HttpServerRequest request = mock(HttpServerRequest.class);
        InetAddress addr = InetAddress.getByName(ip);
        when(request.remoteAddress()).thenReturn(new InetSocketAddress(addr, 12345));
        return new ServerWebExchange(request, null);
    }
}
