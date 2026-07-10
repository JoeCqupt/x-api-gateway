package io.github.xinfra.lab.gateway.benchmark;

import io.github.xinfra.lab.gateway.bootstrap.GatewayServerBootstrap;
import io.github.xinfra.lab.gateway.route.RouteLocator;
import io.github.xinfra.lab.gateway.route.RouteLocatorBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Thread)
public class ProxyBenchmark {

    private DisposableServer upstreamServer;
    private GatewayServerBootstrap gateway;
    private HttpClient client;
    private int gatewayPort;

    @Setup(Level.Trial)
    public void setup() {
        upstreamServer = HttpServer.create()
                .bindAddress(() -> new InetSocketAddress(0))
                .route(routes -> routes.get("/bench",
                        (req, resp) -> resp.sendString(Mono.just("OK"))))
                .bind().block();

        int upstreamPort = upstreamServer.port();

        RouteLocator routeLocator = RouteLocatorBuilder.builder()
                .route("bench-route",
                        spec -> spec.path("/bench.*")
                                .filters()
                                .endpoint()
                                .http(Collections.singletonList(
                                                "http://127.0.0.1:" + upstreamPort),
                                        Collections.<Integer>emptyList()))
                .build();

        gateway = new GatewayServerBootstrap()
                .port(0)
                .routeLocator(routeLocator)
                .start();

        gatewayPort = findGatewayPort();
        client = HttpClient.create();
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (gateway != null) gateway.stop();
        if (upstreamServer != null) upstreamServer.disposeNow();
    }

    @Benchmark
    public Object proxyGetRequest() {
        return client.get()
                .uri("http://127.0.0.1:" + gatewayPort + "/bench")
                .responseSingle((resp, body) -> body.asString())
                .block();
    }

    private int findGatewayPort() {
        try {
            java.lang.reflect.Field serverField = GatewayServerBootstrap.class.getDeclaredField("server");
            serverField.setAccessible(true);
            DisposableServer server = (DisposableServer) serverField.get(gateway);
            return server.port();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get gateway port", e);
        }
    }
}
