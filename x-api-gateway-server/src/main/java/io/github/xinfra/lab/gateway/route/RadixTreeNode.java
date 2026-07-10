package io.github.xinfra.lab.gateway.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RadixTreeNode {
    final String segment;
    final Map<String, RadixTreeNode> children = new HashMap<>();
    RadixTreeNode paramChild;
    RadixTreeNode wildcardChild;
    final List<Route> routes = new ArrayList<>();

    RadixTreeNode(String segment) {
        this.segment = segment;
    }

    void insert(String[] segments, int index, Route route) {
        if (index >= segments.length) {
            routes.add(route);
            return;
        }

        String seg = segments[index];

        if ("**".equals(seg)) {
            if (wildcardChild == null) {
                wildcardChild = new RadixTreeNode("**");
            }
            wildcardChild.routes.add(route);
            return;
        }

        if (seg.startsWith(":")) {
            if (paramChild == null) {
                paramChild = new RadixTreeNode(seg);
            }
            paramChild.insert(segments, index + 1, route);
            return;
        }

        RadixTreeNode child = children.get(seg);
        if (child == null) {
            child = new RadixTreeNode(seg);
            children.put(seg, child);
        }
        child.insert(segments, index + 1, route);
    }

    void search(String[] segments, int index, List<Route> result) {
        if (index >= segments.length) {
            result.addAll(routes);
            return;
        }

        String seg = segments[index];

        RadixTreeNode exactChild = children.get(seg);
        if (exactChild != null) {
            exactChild.search(segments, index + 1, result);
        }

        if (paramChild != null) {
            paramChild.search(segments, index + 1, result);
        }

        if (wildcardChild != null) {
            result.addAll(wildcardChild.routes);
        }
    }
}
