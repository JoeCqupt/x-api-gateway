package io.github.xinfra.lab.gateway.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RadixTreeRouteTable {

    private final RadixTreeNode root = new RadixTreeNode("");

    public void addRoute(Route route, String pathPattern) {
        String[] segments = splitPath(pathPattern);
        root.insert(segments, 0, route);
    }

    public List<Route> match(String requestPath) {
        if (requestPath == null || requestPath.isEmpty()) {
            return Collections.emptyList();
        }
        String[] segments = splitPath(requestPath);
        List<Route> result = new ArrayList<>();
        root.search(segments, 0, result);
        return result;
    }

    static String[] splitPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            return new String[0];
        }
        return path.split("/");
    }
}
