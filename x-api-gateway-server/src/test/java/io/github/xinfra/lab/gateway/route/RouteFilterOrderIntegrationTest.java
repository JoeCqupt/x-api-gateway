package io.github.xinfra.lab.gateway.route;

import io.github.xinfra.lab.gateway.commons.OrderedAwareComparator;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.filter.GatewayFilterDefinition;
import io.github.xinfra.lab.gateway.filter.OrderedGatewayFilter;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public class RouteFilterOrderIntegrationTest {

    @Test
    public void testRouteFiltersGetFactoryOrder() {
        RouteDefinition rd = new RouteDefinition();
        rd.setId("test-route");
        rd.setOrder(1);

        List<GatewayFilterDefinition> filterDefs = new ArrayList<>();

        GatewayFilterDefinition stripPrefix = new GatewayFilterDefinition();
        stripPrefix.setName("StripPrefix");
        stripPrefix.setConfig("parts: 1");
        filterDefs.add(stripPrefix);

        GatewayFilterDefinition requestId = new GatewayFilterDefinition();
        requestId.setName("RequestId");
        requestId.setConfig("headerName: X-Request-Id");
        filterDefs.add(requestId);

        rd.setFilters(filterDefs);

        io.github.xinfra.lab.gateway.endpoint.EndpointDefinition endpoint =
                new io.github.xinfra.lab.gateway.endpoint.EndpointDefinition();
        endpoint.setName("Http");
        endpoint.setConfig("urls:\n  - http://localhost:8080");
        rd.setEndpoint(endpoint);

        RouteDefinitionLocator locator = () -> Flux.just(rd);
        RouteDefinitionRouteLocator routeLocator = new RouteDefinitionRouteLocator(locator);

        Route route = routeLocator.getRoutes().blockFirst();
        Assert.assertNotNull(route);
        List<GatewayFilter> filters = route.getFilters();
        Assert.assertEquals(2, filters.size());

        for (GatewayFilter filter : filters) {
            Assert.assertTrue("Filter should be OrderedGatewayFilter: " + filter.getClass(),
                    filter instanceof OrderedGatewayFilter);
        }

        List<GatewayFilter> sorted = new ArrayList<>(filters);
        OrderedAwareComparator.sort(sorted);

        int firstOrder = ((OrderedGatewayFilter) sorted.get(0)).getOrder();
        int secondOrder = ((OrderedGatewayFilter) sorted.get(1)).getOrder();
        Assert.assertEquals(-900, firstOrder);
        Assert.assertEquals(-50, secondOrder);
    }

    @Test
    public void testRouteFiltersProperSortOrder() {
        RouteDefinition rd = new RouteDefinition();
        rd.setId("sort-test");
        rd.setOrder(1);

        List<GatewayFilterDefinition> filterDefs = new ArrayList<>();

        GatewayFilterDefinition removeHeader = new GatewayFilterDefinition();
        removeHeader.setName("RemoveHeader");
        removeHeader.setConfig("names:\n  - X-Internal");
        filterDefs.add(removeHeader);

        GatewayFilterDefinition rewritePath = new GatewayFilterDefinition();
        rewritePath.setName("RewritePath");
        rewritePath.setConfig("regexp: /api/(.*)\nreplacement: /$1");
        filterDefs.add(rewritePath);

        rd.setFilters(filterDefs);

        io.github.xinfra.lab.gateway.endpoint.EndpointDefinition endpoint =
                new io.github.xinfra.lab.gateway.endpoint.EndpointDefinition();
        endpoint.setName("Http");
        endpoint.setConfig("urls:\n  - http://localhost:8080");
        rd.setEndpoint(endpoint);

        RouteDefinitionLocator locator = () -> Flux.just(rd);
        RouteDefinitionRouteLocator routeLocator = new RouteDefinitionRouteLocator(locator);

        Route route = routeLocator.getRoutes().blockFirst();
        List<GatewayFilter> filters = route.getFilters();

        List<GatewayFilter> sorted = new ArrayList<>(filters);
        OrderedAwareComparator.sort(sorted);

        int firstOrder = ((OrderedGatewayFilter) sorted.get(0)).getOrder();
        int secondOrder = ((OrderedGatewayFilter) sorted.get(1)).getOrder();
        Assert.assertEquals(-40, firstOrder);
        Assert.assertEquals(-20, secondOrder);
    }
}
