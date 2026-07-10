package io.github.xinfra.lab.gateway.handler;

import io.github.xinfra.lab.gateway.commons.Ordered;
import io.github.xinfra.lab.gateway.exception.ErrorCode;
import io.github.xinfra.lab.gateway.exception.ErrorResponseException;
import io.github.xinfra.lab.gateway.route.CacheRouteLocator;
import io.github.xinfra.lab.gateway.route.Route;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static io.github.xinfra.lab.gateway.commons.ServerWebExchangeUtils.desc;

@Slf4j
public class RoutePredicateWebHandler extends WebHandlerDecorator {

    private RouteLocator routeLocator;

    public RoutePredicateWebHandler(WebHandler delegate, RouteLocator routeLocator) {
        super(delegate);
        this.routeLocator = routeLocator;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        Flux<Route> candidates;

        if (routeLocator instanceof CacheRouteLocator) {
            String path = exchange.getRequest().fullPath();
            List<Route> preFiltered = ((CacheRouteLocator) routeLocator).matchByPath(path);
            candidates = Flux.fromIterable(preFiltered);
        } else {
            candidates = routeLocator.getRoutes();
        }

        return candidates
                .filter(route -> {
                    try {
                        return route.getPredicate().test(exchange);
                    } catch (Exception e) {
                        log.error("Error applying predicate for route: {}", route.getId(), e);
                        return false;
                    }
                })
                .sort(Comparator.comparingInt(r ->
                        r instanceof Ordered ? ((Ordered) r).getOrder() : Ordered.LOWEST_PRECEDENCE))
                .next()
                .map(route -> {
                    log.info("Route matched: {}", route.getId());
                    exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
                    return route;
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            log.info("No RouteDefinition found for [{}]", desc(exchange));
                            return Mono.error(new ErrorResponseException(
                                    HttpResponseStatus.NOT_FOUND, ErrorCode.ROUTE_NO_MATCH));
                        })
                )
                .flatMap(r -> super.handle(exchange));
    }
}
