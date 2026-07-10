package io.github.xinfra.lab.gateway.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
public class GatewayEventBus {

    public static final GatewayEventBus INSTANCE = new GatewayEventBus();

    private final List<Consumer<RouteRefreshEvent>> routeRefreshListeners = new CopyOnWriteArrayList<>();

    public void onRouteRefresh(Consumer<RouteRefreshEvent> listener) {
        routeRefreshListeners.add(listener);
    }

    public void removeRouteRefreshListener(Consumer<RouteRefreshEvent> listener) {
        routeRefreshListeners.remove(listener);
    }

    public void publishRouteRefresh(RouteRefreshEvent event) {
        for (Consumer<RouteRefreshEvent> listener : routeRefreshListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in route refresh listener", e);
            }
        }
    }
}
