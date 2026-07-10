package io.github.xinfra.lab.gateway.metrics;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class MetricsCollectorTest {

    private MetricsCollector collector;

    @Before
    public void setup() {
        collector = new MetricsCollector();
        collector.reset();
    }

    @Test
    public void testCounterIncrement() {
        collector.incrementCounter("test_counter");
        collector.incrementCounter("test_counter");
        collector.incrementCounter("test_counter");

        String output = collector.exportPrometheus();
        assertTrue(output.contains("test_counter 3"));
    }

    @Test
    public void testGaugeIncrementDecrement() {
        collector.incrementGauge("test_gauge");
        collector.incrementGauge("test_gauge");
        collector.decrementGauge("test_gauge");

        String output = collector.exportPrometheus();
        assertTrue(output.contains("test_gauge 1"));
    }

    @Test
    public void testRecordDuration() {
        collector.recordDuration("test_duration", 3);
        collector.recordDuration("test_duration", 50);

        String output = collector.exportPrometheus();
        assertTrue(output.contains("test_duration_bucket{le=\"5\"} 1"));
        assertTrue(output.contains("test_duration_bucket{le=\"50\"} 2"));
        assertTrue(output.contains("test_duration_count 2"));
        assertTrue(output.contains("test_duration_sum 53"));
    }

    @Test
    public void testRecordRequest() {
        collector.recordRequest("my-route", 200, "GET", 15);
        collector.recordRequest("my-route", 500, "POST", 100);

        String output = collector.exportPrometheus();
        assertTrue(output.contains("route_id=\"my-route\""));
        assertTrue(output.contains("status_code=\"200\""));
        assertTrue(output.contains("outcome=\"SUCCESS\""));
        assertTrue(output.contains("status_code=\"500\""));
        assertTrue(output.contains("outcome=\"SERVER_ERROR\""));
    }

    @Test
    public void testPrometheusFormat() {
        collector.incrementCounter("x_gateway_requests_total{route_id=\"test\"}");

        String output = collector.exportPrometheus();
        assertTrue(output.contains("# HELP x_gateway_requests_total"));
        assertTrue(output.contains("# TYPE x_gateway_requests_total counter"));
    }

    @Test
    public void testExportJsonCounters() {
        collector.incrementCounter("req_total");
        collector.incrementCounter("req_total");

        String json = collector.exportJson();
        assertTrue(json.contains("\"counters\":{"));
        assertTrue(json.contains("\"req_total\":2"));
    }

    @Test
    public void testExportJsonGauges() {
        collector.incrementGauge("active");
        collector.incrementGauge("active");
        collector.decrementGauge("active");

        String json = collector.exportJson();
        assertTrue(json.contains("\"gauges\":{"));
        assertTrue(json.contains("\"active\":1"));
    }

    @Test
    public void testExportJsonHistograms() {
        collector.recordDuration("latency", 10);
        collector.recordDuration("latency", 200);

        String json = collector.exportJson();
        assertTrue(json.contains("\"histograms\":{"));
        assertTrue(json.contains("\"latency\":{\"buckets\":{"));
        assertTrue(json.contains("\"count\":2"));
        assertTrue(json.contains("\"sum\":210"));
    }

    @Test
    public void testExportJsonEmptyCollector() {
        String json = collector.exportJson();
        assertEquals("{\"counters\":{},\"gauges\":{},\"histograms\":{}}", json);
    }

    @Test
    public void testResetClearsAll() {
        collector.incrementCounter("c");
        collector.incrementGauge("g");
        collector.recordDuration("h", 5);

        collector.reset();

        String json = collector.exportJson();
        assertEquals("{\"counters\":{},\"gauges\":{},\"histograms\":{}}", json);
    }

    @Test
    public void testClientErrorOutcome() {
        collector.recordRequest("route1", 404, "GET", 5);
        String output = collector.exportPrometheus();
        assertTrue(output.contains("outcome=\"CLIENT_ERROR\""));
    }
}
