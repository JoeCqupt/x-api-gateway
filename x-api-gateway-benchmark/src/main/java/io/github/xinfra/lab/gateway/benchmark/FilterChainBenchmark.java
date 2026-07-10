package io.github.xinfra.lab.gateway.benchmark;

import io.github.xinfra.lab.gateway.filter.DefaultGatewayFilterChain;
import io.github.xinfra.lab.gateway.filter.GatewayFilter;
import io.github.xinfra.lab.gateway.server.ServerWebExchange;
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
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class FilterChainBenchmark {

    @Param({"1", "5", "10", "20"})
    private int filterCount;

    private List<GatewayFilter> filters;
    private ServerWebExchange exchange;

    @Setup(Level.Trial)
    public void setup() {
        filters = new ArrayList<>();
        for (int i = 0; i < filterCount; i++) {
            filters.add((ex, chain) -> chain.filter(ex));
        }
        exchange = new ServerWebExchange(null, null);
    }

    @Benchmark
    public Object executeFilterChain() {
        DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(filters);
        return chain.filter(exchange).block();
    }
}
