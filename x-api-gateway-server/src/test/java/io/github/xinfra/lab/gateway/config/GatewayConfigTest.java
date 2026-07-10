package io.github.xinfra.lab.gateway.config;

import org.junit.Assert;
import org.junit.Test;

public class GatewayConfigTest {

    @Test
    public void testServerConfigDefaults() {
        GatewayConfig.ServerConfig config = new GatewayConfig.ServerConfig();

        Assert.assertEquals(8080, config.getPort());
        Assert.assertEquals(30000, config.getShutdownTimeoutMs());
        Assert.assertFalse(config.isHttp2Enabled());
        Assert.assertEquals(1, config.getBossThreads());
        Assert.assertEquals(0, config.getWorkerThreads());
        Assert.assertEquals(10 * 1024 * 1024, config.getMaxContentLength());
        Assert.assertEquals(1024, config.getBacklog());
        Assert.assertTrue(config.isTcpNoDelay());
        Assert.assertTrue(config.isSoKeepAlive());
    }

    @Test
    public void testUpstreamConfigDefaults() {
        GatewayConfig.UpstreamConfig config = new GatewayConfig.UpstreamConfig();

        Assert.assertEquals(3000, config.getConnectTimeout());
        Assert.assertEquals(10000, config.getReadTimeout());
        Assert.assertEquals(256, config.getPoolMaxConnections());
        Assert.assertEquals(60000, config.getPoolMaxIdleTime());
        Assert.assertEquals(300000, config.getPoolMaxLifeTime());
        Assert.assertEquals(30000, config.getPoolEvictionInterval());
    }

    @Test
    public void testGatewayConfigUpstreamField() {
        GatewayConfig config = new GatewayConfig();
        Assert.assertNull(config.getUpstream());

        GatewayConfig.UpstreamConfig upstream = new GatewayConfig.UpstreamConfig();
        upstream.setConnectTimeout(5000);
        config.setUpstream(upstream);

        Assert.assertNotNull(config.getUpstream());
        Assert.assertEquals(5000, config.getUpstream().getConnectTimeout());
    }

    @Test
    public void testObservabilityConfigDefaults() {
        GatewayConfig.ObservabilityConfig.MetricsConfig metrics =
                new GatewayConfig.ObservabilityConfig.MetricsConfig();
        Assert.assertTrue(metrics.isEnabled());
        Assert.assertEquals("prometheus", metrics.getExporterType());
        Assert.assertEquals("x_gateway", metrics.getPrefix());

        GatewayConfig.ObservabilityConfig.AccessLogConfig accessLog =
                new GatewayConfig.ObservabilityConfig.AccessLogConfig();
        Assert.assertTrue(accessLog.isEnabled());
        Assert.assertEquals("json", accessLog.getFormat());
        Assert.assertEquals("stdout", accessLog.getOutput());
        Assert.assertNull(accessLog.getFilePath());
    }

    @Test
    public void testObservabilityConfigWiring() {
        GatewayConfig config = new GatewayConfig();
        Assert.assertNull(config.getObservability());

        GatewayConfig.ObservabilityConfig obs = new GatewayConfig.ObservabilityConfig();
        GatewayConfig.ObservabilityConfig.MetricsConfig metrics =
                new GatewayConfig.ObservabilityConfig.MetricsConfig();
        metrics.setExporterType("otlp");
        metrics.setPrefix("my_gw");
        obs.setMetrics(metrics);

        GatewayConfig.ObservabilityConfig.AccessLogConfig accessLog =
                new GatewayConfig.ObservabilityConfig.AccessLogConfig();
        accessLog.setFormat("combined");
        accessLog.setOutput("file");
        accessLog.setFilePath("/var/log/access.log");
        obs.setAccessLog(accessLog);

        config.setObservability(obs);

        Assert.assertNotNull(config.getObservability());
        Assert.assertEquals("otlp", config.getObservability().getMetrics().getExporterType());
        Assert.assertEquals("my_gw", config.getObservability().getMetrics().getPrefix());
        Assert.assertEquals("combined", config.getObservability().getAccessLog().getFormat());
        Assert.assertEquals("file", config.getObservability().getAccessLog().getOutput());
        Assert.assertEquals("/var/log/access.log", config.getObservability().getAccessLog().getFilePath());
    }

    @Test
    public void testTlsConfigDefaults() {
        GatewayConfig.TlsConfig tls = new GatewayConfig.TlsConfig();
        Assert.assertFalse(tls.isEnabled());
        Assert.assertNull(tls.getCertPath());
        Assert.assertNull(tls.getKeyPath());
        Assert.assertNull(tls.getProtocols());
    }

    @Test
    public void testServerConfigTlsField() {
        GatewayConfig.ServerConfig sc = new GatewayConfig.ServerConfig();
        Assert.assertNull(sc.getTls());

        GatewayConfig.TlsConfig tls = new GatewayConfig.TlsConfig();
        tls.setEnabled(true);
        tls.setCertPath("/etc/cert.pem");
        tls.setKeyPath("/etc/key.pem");
        sc.setTls(tls);

        Assert.assertNotNull(sc.getTls());
        Assert.assertTrue(sc.getTls().isEnabled());
        Assert.assertEquals("/etc/cert.pem", sc.getTls().getCertPath());
        Assert.assertEquals("/etc/key.pem", sc.getTls().getKeyPath());
    }
}
