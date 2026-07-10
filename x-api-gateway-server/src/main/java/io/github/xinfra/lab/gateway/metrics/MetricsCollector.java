package io.github.xinfra.lab.gateway.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {

    public static final MetricsCollector INSTANCE = new MetricsCollector();

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, long[]> histograms = new ConcurrentHashMap<>();

    private static final long[] DURATION_BUCKETS_MS = {1, 5, 10, 25, 50, 100, 250, 500, 1000, 5000, 10000};

    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public void incrementGauge(String name) {
        gauges.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void decrementGauge(String name) {
        gauges.computeIfAbsent(name, k -> new AtomicLong(0)).decrementAndGet();
    }

    public void recordDuration(String name, long durationMs) {
        long[] bucketCounts = histograms.computeIfAbsent(name,
                k -> new long[DURATION_BUCKETS_MS.length + 2]);
        synchronized (bucketCounts) {
            for (int i = 0; i < DURATION_BUCKETS_MS.length; i++) {
                if (durationMs <= DURATION_BUCKETS_MS[i]) {
                    bucketCounts[i]++;
                }
            }
            bucketCounts[DURATION_BUCKETS_MS.length]++;     // +Inf count (total)
            bucketCounts[DURATION_BUCKETS_MS.length + 1] += durationMs; // sum
        }
    }

    public void recordRequest(String routeId, int statusCode, String method, long durationMs) {
        String outcome = statusCode < 400 ? "SUCCESS" : statusCode < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
        incrementCounter(formatKey("x_gateway_requests_total", routeId, statusCode, method, outcome));
        recordDuration(formatDurationKey("x_gateway_request_duration_ms", routeId, statusCode), durationMs);
    }

    public String exportPrometheus() {
        StringBuilder sb = new StringBuilder();

        sb.append("# HELP x_gateway_requests_total Total number of requests\n");
        sb.append("# TYPE x_gateway_requests_total counter\n");
        for (Map.Entry<String, LongAdder> entry : counters.entrySet()) {
            sb.append(entry.getKey()).append(' ').append(entry.getValue().sum()).append('\n');
        }

        sb.append("# HELP x_gateway_active_requests Current active requests\n");
        sb.append("# TYPE x_gateway_active_requests gauge\n");
        for (Map.Entry<String, AtomicLong> entry : gauges.entrySet()) {
            sb.append(entry.getKey()).append(' ').append(entry.getValue().get()).append('\n');
        }

        sb.append("# HELP x_gateway_request_duration_ms Request duration in milliseconds\n");
        sb.append("# TYPE x_gateway_request_duration_ms histogram\n");
        for (Map.Entry<String, long[]> entry : histograms.entrySet()) {
            String name = entry.getKey();
            long[] bucketCounts = entry.getValue();
            synchronized (bucketCounts) {
                for (int i = 0; i < DURATION_BUCKETS_MS.length; i++) {
                    sb.append(name).append("_bucket{le=\"").append(DURATION_BUCKETS_MS[i]).append("\"} ")
                            .append(bucketCounts[i]).append('\n');
                }
                sb.append(name).append("_bucket{le=\"+Inf\"} ")
                        .append(bucketCounts[DURATION_BUCKETS_MS.length]).append('\n');
                sb.append(name).append("_count ")
                        .append(bucketCounts[DURATION_BUCKETS_MS.length]).append('\n');
                sb.append(name).append("_sum ")
                        .append(bucketCounts[DURATION_BUCKETS_MS.length + 1]).append('\n');
            }
        }

        return sb.toString();
    }

    private String formatKey(String metric, String routeId, int statusCode, String method, String outcome) {
        return metric + "{route_id=\"" + routeId + "\",status_code=\"" + statusCode
                + "\",method=\"" + method + "\",outcome=\"" + outcome + "\"}";
    }

    private String formatDurationKey(String metric, String routeId, int statusCode) {
        return metric + "{route_id=\"" + routeId + "\",status_code=\"" + statusCode + "\"}";
    }

    public String exportJson() {
        StringBuilder sb = new StringBuilder("{\"counters\":{");
        boolean first = true;
        for (Map.Entry<String, LongAdder> entry : counters.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":").append(entry.getValue().sum());
        }
        sb.append("},\"gauges\":{");
        first = true;
        for (Map.Entry<String, AtomicLong> entry : gauges.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":").append(entry.getValue().get());
        }
        sb.append("},\"histograms\":{");
        first = true;
        for (Map.Entry<String, long[]> entry : histograms.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            String name = entry.getKey();
            long[] bucketCounts = entry.getValue();
            sb.append('"').append(escapeJson(name)).append("\":{\"buckets\":{");
            synchronized (bucketCounts) {
                for (int i = 0; i < DURATION_BUCKETS_MS.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append('"').append(DURATION_BUCKETS_MS[i]).append("\":").append(bucketCounts[i]);
                }
                sb.append("},\"count\":").append(bucketCounts[DURATION_BUCKETS_MS.length]);
                sb.append(",\"sum\":").append(bucketCounts[DURATION_BUCKETS_MS.length + 1]);
            }
            sb.append('}');
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String escapeJson(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void reset() {
        counters.clear();
        gauges.clear();
        histograms.clear();
    }
}
