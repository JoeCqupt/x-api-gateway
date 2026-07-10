package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CookiePredicateTest {

    @Test
    public void testCookieMatch() {
        CookieRoutePredicateFactory factory = new CookieRoutePredicateFactory();
        CookieRoutePredicateFactory.Config config = new CookieRoutePredicateFactory.Config();
        config.setName("session");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchangeWithCookie("session=abc123");
        Assert.assertTrue(predicate.test(exchange));
    }

    @Test
    public void testCookieNoMatch() {
        CookieRoutePredicateFactory factory = new CookieRoutePredicateFactory();
        CookieRoutePredicateFactory.Config config = new CookieRoutePredicateFactory.Config();
        config.setName("session");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchangeWithCookie("token=xyz");
        Assert.assertFalse(predicate.test(exchange));
    }

    @Test
    public void testNoCookieHeader() {
        CookieRoutePredicateFactory factory = new CookieRoutePredicateFactory();
        CookieRoutePredicateFactory.Config config = new CookieRoutePredicateFactory.Config();
        config.setName("session");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);
        ServerWebExchange exchange = new ServerWebExchange(request, null);

        Assert.assertFalse(predicate.test(exchange));
    }

    @Test
    public void testCookieRegexMatch() {
        CookieRoutePredicateFactory factory = new CookieRoutePredicateFactory();
        CookieRoutePredicateFactory.Config config = new CookieRoutePredicateFactory.Config();
        config.setName("session");
        config.setRegex("[a-z]+\\d+");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        Assert.assertTrue(predicate.test(mockExchangeWithCookie("session=abc123")));
        Assert.assertFalse(predicate.test(mockExchangeWithCookie("session=ABC")));
    }

    @Test
    public void testMultipleCookies() {
        CookieRoutePredicateFactory factory = new CookieRoutePredicateFactory();
        CookieRoutePredicateFactory.Config config = new CookieRoutePredicateFactory.Config();
        config.setName("token");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchangeWithCookie("session=abc; token=xyz; lang=en");
        Assert.assertTrue(predicate.test(exchange));
    }

    @Test
    public void testFactoryName() {
        CookieRoutePredicateFactory factory = new CookieRoutePredicateFactory();
        Assert.assertEquals("Cookie", factory.getName());
    }

    private ServerWebExchange mockExchangeWithCookie(String cookieValue) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("Cookie", cookieValue);
        when(request.requestHeaders()).thenReturn(headers);
        return new ServerWebExchange(request, null);
    }
}
