package io.github.xinfra.lab.gateway.route;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class RadixTreeRouteTableTest {

    private Route mockRoute(String id) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(id);
        return route;
    }

    @Test
    public void testExactPathMatch() {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        Route route = mockRoute("exact");
        table.addRoute(route, "/api/users");

        List<Route> result = table.match("/api/users");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("exact", result.get(0).getId());
    }

    @Test
    public void testNoMatch() {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        Route route = mockRoute("r1");
        table.addRoute(route, "/api/users");

        List<Route> result = table.match("/api/orders");
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testWildcardMatch() {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        Route route = mockRoute("wildcard");
        table.addRoute(route, "/api/**");

        List<Route> result = table.match("/api/users/123");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("wildcard", result.get(0).getId());
    }

    @Test
    public void testParamMatch() {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        Route route = mockRoute("param");
        table.addRoute(route, "/api/users/:id");

        List<Route> result = table.match("/api/users/42");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("param", result.get(0).getId());
    }

    @Test
    public void testMultipleRoutes() {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        table.addRoute(mockRoute("users"), "/api/users/**");
        table.addRoute(mockRoute("orders"), "/api/orders/**");

        List<Route> users = table.match("/api/users/list");
        Assert.assertEquals(1, users.size());
        Assert.assertEquals("users", users.get(0).getId());

        List<Route> orders = table.match("/api/orders/123");
        Assert.assertEquals(1, orders.size());
        Assert.assertEquals("orders", orders.get(0).getId());
    }

    @Test
    public void testExactAndWildcardBothMatch() {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        table.addRoute(mockRoute("exact"), "/api/users/list");
        table.addRoute(mockRoute("wildcard"), "/api/**");

        List<Route> result = table.match("/api/users/list");
        Assert.assertTrue(result.size() >= 1);
        boolean hasExact = result.stream().anyMatch(r -> "exact".equals(r.getId()));
        Assert.assertTrue("Should contain exact match", hasExact);
    }

    @Test
    public void testSplitPath() {
        String[] segments = RadixTreeRouteTable.splitPath("/api/users/123");
        Assert.assertArrayEquals(new String[]{"api", "users", "123"}, segments);
    }

    @Test
    public void testSplitPathRoot() {
        String[] segments = RadixTreeRouteTable.splitPath("/");
        Assert.assertEquals(0, segments.length);
    }

    @Test
    public void testParamAndExactPriority() {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        table.addRoute(mockRoute("exact"), "/api/users/admin");
        table.addRoute(mockRoute("param"), "/api/users/:id");

        List<Route> result = table.match("/api/users/admin");
        Assert.assertEquals(2, result.size());
    }
}
