package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.endpoint.Endpoint;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.route.DefaultRoute;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.mockito.Mockito.*;

public class RoutePredicateWebHandlerTest {

    private ServerWebExchange mockExchange() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.fullPath()).thenReturn("/api/test");
        when(request.uri()).thenReturn("/api/test");
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.requestHeaders()).thenReturn(new DefaultHttpHeaders());
        return new ServerWebExchange(request, null);
    }

    @Test
    public void testRouteOrderingSelectsLowestOrder() {
        DefaultRoute routeHigh = new DefaultRoute();
        routeHigh.setId("high-order");
        routeHigh.setOrder(100);
        routeHigh.setPredicate(ex -> true);
        routeHigh.setFilters(Collections.<GatewayFilter>emptyList());
        routeHigh.setEndpoint(mock(Endpoint.class));

        DefaultRoute routeLow = new DefaultRoute();
        routeLow.setId("low-order");
        routeLow.setOrder(1);
        routeLow.setPredicate(ex -> true);
        routeLow.setFilters(Collections.<GatewayFilter>emptyList());
        routeLow.setEndpoint(mock(Endpoint.class));

        RouteLocator locator = () -> Flux.just((Route) routeHigh, (Route) routeLow);

        AtomicReference<String> matchedRouteId = new AtomicReference<>();

        WebHandler delegate = exchange -> {
            Route matched = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            matchedRouteId.set(matched.getId());
            return Mono.empty();
        };

        RoutePredicateWebHandler handler = new RoutePredicateWebHandler(delegate, locator);
        ServerWebExchange exchange = mockExchange();

        handler.handle(exchange).block();

        Assert.assertEquals("low-order", matchedRouteId.get());
    }

    @Test
    public void testRouteNoMatchReturns404() {
        RouteLocator locator = () -> Flux.just(createRoute("no-match", ex -> false));

        WebHandler delegate = exchange -> Mono.empty();
        RoutePredicateWebHandler handler = new RoutePredicateWebHandler(delegate, locator);

        try {
            handler.handle(mockExchange()).block();
            Assert.fail("Should have thrown");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause().toString().contains("ErrorResponseException")
                    || e.toString().contains("ErrorResponseException"));
        }
    }

    private Route createRoute(String id, io.github.xinfra.lab.gateway.predicate.RoutePredicate<ServerWebExchange> predicate) {
        DefaultRoute route = new DefaultRoute();
        route.setId(id);
        route.setPredicate(predicate);
        route.setFilters(Collections.<GatewayFilter>emptyList());
        route.setEndpoint(mock(Endpoint.class));
        return route;
    }
}
