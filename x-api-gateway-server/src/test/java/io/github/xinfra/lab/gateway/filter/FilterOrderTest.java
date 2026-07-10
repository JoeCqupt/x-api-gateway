package io.github.xinfra.lab.gateway.filter;

import io.github.xinfra.lab.gateway.commons.OrderedAwareComparator;
import io.github.xinfra.lab.gateway.filter.global.AccessLogGatewayFilter;
import io.github.xinfra.lab.gateway.filter.global.GlobalGatewayFilter;
import io.github.xinfra.lab.gateway.filter.global.RoutingFilter;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FilterOrderTest {

    @Test
    public void testRoutingFilterOrder200() {
        RoutingFilter routing = new RoutingFilter();
        Assert.assertEquals(200, routing.getOrder());
    }

    @Test
    public void testAccessLogFilterOrderMinus800() {
        AccessLogGatewayFilter accessLog = new AccessLogGatewayFilter();
        Assert.assertEquals(-800, accessLog.getOrder());
    }

    @Test
    public void testOrderedGatewayFilterWrapsCorrectly() {
        GatewayFilter inner = (exchange, chain) -> Mono.empty();
        OrderedGatewayFilter ordered = new OrderedGatewayFilter(inner, -500);

        Assert.assertEquals(-500, ordered.getOrder());
        Assert.assertSame(inner, ordered.getDelegate());
    }

    @Test
    public void testFactoryOrderPropagatedToFilter() {
        RequestIdGatewayFilterFactory factory = new RequestIdGatewayFilterFactory();
        GatewayFilter filter = factory.apply(null);
        GatewayFilter wrapped = wrapWithFactoryOrder(filter, factory);

        Assert.assertTrue(wrapped instanceof OrderedGatewayFilter);
        Assert.assertEquals(-900, ((OrderedGatewayFilter) wrapped).getOrder());
    }

    @Test
    public void testFilterSortingWithOrders() {
        GatewayFilter requestId = new OrderedGatewayFilter((e, c) -> Mono.empty(), -900);
        GatewayFilter auth = new OrderedGatewayFilter((e, c) -> Mono.empty(), -500);
        GatewayFilter rateLimit = new OrderedGatewayFilter((e, c) -> Mono.empty(), -200);
        GatewayFilter stripPrefix = new OrderedGatewayFilter((e, c) -> Mono.empty(), -50);
        GatewayFilter addHeader = new OrderedGatewayFilter((e, c) -> Mono.empty(), -30);
        RoutingFilter routing = new RoutingFilter();
        GatewayFilter addResponse = new OrderedGatewayFilter((e, c) -> Mono.empty(), 500);

        List<GatewayFilter> filters = new ArrayList<>(Arrays.asList(
                addResponse, stripPrefix, routing, auth, requestId, rateLimit, addHeader));
        OrderedAwareComparator.sort(filters);

        Assert.assertSame(requestId, filters.get(0));
        Assert.assertSame(auth, filters.get(1));
        Assert.assertSame(rateLimit, filters.get(2));
        Assert.assertSame(stripPrefix, filters.get(3));
        Assert.assertSame(addHeader, filters.get(4));
        Assert.assertSame(routing, filters.get(5));
        Assert.assertSame(addResponse, filters.get(6));
    }

    @Test
    public void testGlobalFiltersWithRoutingOrder() {
        AccessLogGatewayFilter accessLog = new AccessLogGatewayFilter();
        RoutingFilter routing = new RoutingFilter();

        List<GatewayFilter> filters = new ArrayList<>(Arrays.asList(routing, accessLog));
        OrderedAwareComparator.sort(filters);

        Assert.assertSame(accessLog, filters.get(0));
        Assert.assertSame(routing, filters.get(1));
    }

    @Test
    public void testMixedGlobalAndRouteFilters() {
        AccessLogGatewayFilter accessLog = new AccessLogGatewayFilter();
        RoutingFilter routing = new RoutingFilter();
        GatewayFilter stripPrefix = new OrderedGatewayFilter((e, c) -> Mono.empty(), -50);
        GatewayFilter addResponse = new OrderedGatewayFilter((e, c) -> Mono.empty(), 500);

        List<GatewayFilter> filters = new ArrayList<>(
                Arrays.asList(addResponse, routing, stripPrefix, accessLog));
        OrderedAwareComparator.sort(filters);

        Assert.assertSame(accessLog, filters.get(0));
        Assert.assertSame(stripPrefix, filters.get(1));
        Assert.assertSame(routing, filters.get(2));
        Assert.assertSame(addResponse, filters.get(3));
    }

    @Test
    public void testAllFactoryOrders() {
        assertFactoryOrder(new RequestIdGatewayFilterFactory(), -900);
        assertFactoryOrder(new CorsGatewayFilterFactory(), -600);
        assertFactoryOrder(new AuthGatewayFilterFactory(), -500);
        assertFactoryOrder(new IpFilterGatewayFilterFactory(), -400);
        assertFactoryOrder(new SecureHeadersGatewayFilterFactory(), -300);
        assertFactoryOrder(new RateLimitGatewayFilterFactory(), -200);
        assertFactoryOrder(new BulkheadGatewayFilterFactory(), -150);
        assertFactoryOrder(new CircuitBreakerGatewayFilterFactory(), -100);
        assertFactoryOrder(new StripPrefixGatewayFilterFactory(), -50);
        assertFactoryOrder(new RewritePathGatewayFilterFactory(), -40);
        assertFactoryOrder(new AddHeaderGatewayFilterFactory(), -30);
        assertFactoryOrder(new RemoveHeaderGatewayFilterFactory(), -20);
        assertFactoryOrder(new RequestSizeGatewayFilterFactory(), -10);
        assertFactoryOrder(new RetryGatewayFilterFactory(), 150);
        assertFactoryOrder(new AddResponseHeaderGatewayFilterFactory(), 500);
        assertFactoryOrder(new RemoveResponseHeaderGatewayFilterFactory(), 510);
        assertFactoryOrder(new ResponseBodyRewriteGatewayFilterFactory(), 520);
    }

    private void assertFactoryOrder(GatewayFilterFactory<?> factory, int expectedOrder) {
        io.github.xinfra.lab.gateway.annotations.Order order =
                factory.getClass().getAnnotation(io.github.xinfra.lab.gateway.annotations.Order.class);
        Assert.assertNotNull("Missing @Order on " + factory.getClass().getSimpleName(), order);
        Assert.assertEquals("Wrong order on " + factory.getClass().getSimpleName(),
                expectedOrder, order.value());
    }

    private GatewayFilter wrapWithFactoryOrder(GatewayFilter filter, GatewayFilterFactory<?> factory) {
        if (filter instanceof io.github.xinfra.lab.gateway.commons.Ordered) {
            return filter;
        }
        io.github.xinfra.lab.gateway.annotations.Order orderAnnotation =
                factory.getClass().getAnnotation(io.github.xinfra.lab.gateway.annotations.Order.class);
        if (orderAnnotation != null) {
            return new OrderedGatewayFilter(filter, orderAnnotation.value());
        }
        return filter;
    }
}
