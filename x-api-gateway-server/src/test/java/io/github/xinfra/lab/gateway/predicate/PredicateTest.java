package io.github.xinfra.lab.gateway.predicate;

import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PredicateTest {

    @Test
    public void testPathPredicate() {
        PathRoutePredicateFactory factory = new PathRoutePredicateFactory();
        PathRoutePredicateFactory.PatternConfig config = new PathRoutePredicateFactory.PatternConfig();
        config.setPattern("/api/users/.*");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/api/users/123", "GET");
        Assert.assertTrue(predicate.test(exchange));

        exchange = mockExchange("/api/orders/123", "GET");
        Assert.assertFalse(predicate.test(exchange));
    }

    @Test
    public void testMethodPredicate() {
        MethodRoutePredicateFactory factory = new MethodRoutePredicateFactory();
        MethodRoutePredicateFactory.Config config = new MethodRoutePredicateFactory.Config();
        config.setMethods(Arrays.asList("GET", "POST"));

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchange("/test", "GET");
        Assert.assertTrue(predicate.test(exchange));

        exchange = mockExchange("/test", "POST");
        Assert.assertTrue(predicate.test(exchange));

        exchange = mockExchange("/test", "DELETE");
        Assert.assertFalse(predicate.test(exchange));
    }

    @Test
    public void testHeaderPredicate() {
        HeaderRoutePredicateFactory factory = new HeaderRoutePredicateFactory();
        HeaderRoutePredicateFactory.Config config = new HeaderRoutePredicateFactory.Config();
        config.setName("X-Token");
        config.setRegex("abc.*");

        RoutePredicate<ServerWebExchange> predicate = factory.apply(config);

        ServerWebExchange exchange = mockExchangeWithHeader("/test", "GET", "X-Token", "abc123");
        Assert.assertTrue(predicate.test(exchange));

        exchange = mockExchangeWithHeader("/test", "GET", "X-Token", "xyz");
        Assert.assertFalse(predicate.test(exchange));

        exchange = mockExchange("/test", "GET");
        Assert.assertFalse(predicate.test(exchange));
    }

    @Test
    public void testPredicateComposition() {
        PathRoutePredicateFactory pathFactory = new PathRoutePredicateFactory();
        PathRoutePredicateFactory.PatternConfig pathConfig = new PathRoutePredicateFactory.PatternConfig();
        pathConfig.setPattern("/api/.*");
        RoutePredicate<ServerWebExchange> pathPredicate = pathFactory.apply(pathConfig);

        MethodRoutePredicateFactory methodFactory = new MethodRoutePredicateFactory();
        MethodRoutePredicateFactory.Config methodConfig = new MethodRoutePredicateFactory.Config();
        methodConfig.setMethods(Collections.singletonList("GET"));
        RoutePredicate<ServerWebExchange> methodPredicate = methodFactory.apply(methodConfig);

        RoutePredicate<ServerWebExchange> combined = pathPredicate.and(methodPredicate);

        ServerWebExchange exchange = mockExchange("/api/users", "GET");
        Assert.assertTrue(combined.test(exchange));

        exchange = mockExchange("/api/users", "POST");
        Assert.assertFalse(combined.test(exchange));

        exchange = mockExchange("/other", "GET");
        Assert.assertFalse(combined.test(exchange));

        // test OR
        RoutePredicate<ServerWebExchange> orPredicate = pathPredicate.or(methodPredicate);
        exchange = mockExchange("/other", "GET");
        Assert.assertTrue(orPredicate.test(exchange));

        // test negate
        RoutePredicate<ServerWebExchange> negated = pathPredicate.negate();
        exchange = mockExchange("/api/users", "GET");
        Assert.assertFalse(negated.test(exchange));

        exchange = mockExchange("/other", "GET");
        Assert.assertTrue(negated.test(exchange));
    }

    private ServerWebExchange mockExchange(String path, String method) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.fullPath()).thenReturn(path);
        when(request.path()).thenReturn(path);
        when(request.method()).thenReturn(HttpMethod.valueOf(method));
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        when(request.requestHeaders()).thenReturn(headers);

        return new ServerWebExchange(request, null);
    }

    private ServerWebExchange mockExchangeWithHeader(String path, String method,
                                                      String headerName, String headerValue) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.fullPath()).thenReturn(path);
        when(request.path()).thenReturn(path);
        when(request.method()).thenReturn(HttpMethod.valueOf(method));
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add(headerName, headerValue);
        when(request.requestHeaders()).thenReturn(headers);

        return new ServerWebExchange(request, null);
    }
}
