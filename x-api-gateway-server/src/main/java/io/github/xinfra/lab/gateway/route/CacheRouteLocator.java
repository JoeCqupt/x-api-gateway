package io.github.xinfra.lab.gateway.route;

import io.github.xinfra.lab.gateway.event.GatewayEventBus;
import io.github.xinfra.lab.gateway.event.RouteRefreshEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

import static io.github.xinfra.lab.gateway.route.RouteDefinitionRouteLocator.PATH_PATTERN_METADATA_KEY;

@Slf4j

public class CacheRouteLocator implements RouteLocator {

    private RouteLocator delegate;
    private volatile List<Route> cache;
    private volatile RadixTreeRouteTable routeTable;

    public CacheRouteLocator(RouteLocator delegate) {
        this.delegate = delegate;
        this.cache = fetch();
        this.routeTable = buildRouteTable(this.cache);
    }

    private List<Route> fetch() {
        return delegate.getRoutes().collectList().block();
    }

    private RadixTreeRouteTable buildRouteTable(List<Route> routes) {
        RadixTreeRouteTable table = new RadixTreeRouteTable();
        if (routes != null) {
            for (Route route : routes) {
                String pattern = null;
                if (route.getMetadata() != null) {
                    Object p = route.getMetadata().get(PATH_PATTERN_METADATA_KEY);
                    if (p instanceof String) {
                        pattern = (String) p;
                    }
                }
                if (pattern != null) {
                    String treePath = regexToTreePath(pattern);
                    table.addRoute(route, treePath);
                }
            }
        }
        return table;
    }

    static String regexToTreePath(String regex) {
        String path = regex;
        path = path.replace("/.*", "/**");
        path = path.replaceAll("\\.\\*$", "/**");
        path = path.replaceAll("\\(\\.\\*\\)", "**");
        return path;
    }

    @Override
    public Flux<Route> getRoutes() {
        return Flux.fromIterable(cache);
    }

    public List<Route> matchByPath(String requestPath) {
        if (routeTable == null) {
            return cache != null ? cache : Collections.emptyList();
        }
        List<Route> matched = routeTable.match(requestPath);
        if (matched.isEmpty()) {
            return cache != null ? cache : Collections.emptyList();
        }
        return matched;
    }

    public void refresh() {
        this.cache = fetch();
        this.routeTable = buildRouteTable(this.cache);
        int count = this.cache != null ? this.cache.size() : 0;
        log.info("Route table refreshed, {} routes loaded", count);
        GatewayEventBus.INSTANCE.publishRouteRefresh(
                new RouteRefreshEvent(count, "CacheRouteLocator"));
    }
}
