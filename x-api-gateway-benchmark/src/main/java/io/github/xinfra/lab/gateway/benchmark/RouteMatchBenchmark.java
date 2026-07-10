package io.github.xinfra.lab.gateway.benchmark;

import io.github.xinfra.lab.gateway.route.DefaultRoute;
import io.github.xinfra.lab.gateway.route.RadixTreeRouteTable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class RouteMatchBenchmark {

    @Param({"10", "100", "1000"})
    private int routeCount;

    private RadixTreeRouteTable routeTable;

    @Setup(Level.Trial)
    public void setup() {
        routeTable = new RadixTreeRouteTable();
        for (int i = 0; i < routeCount; i++) {
            DefaultRoute route = new DefaultRoute();
            route.setId("route-" + i);
            route.setPredicate(exchange -> true);
            route.setFilters(Collections.emptyList());
            routeTable.addRoute(route, "/api/v1/service" + i + "/.*");
        }
    }

    @Benchmark
    public Object matchExistingRoute() {
        return routeTable.match("/api/v1/service0/resource");
    }

    @Benchmark
    public Object matchNonExistingRoute() {
        return routeTable.match("/api/v2/unknown/path");
    }

    @Benchmark
    public Object matchDeepPath() {
        return routeTable.match("/api/v1/service0/a/b/c/d/e");
    }
}
