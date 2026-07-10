package io.github.xinfra.lab.gateway.healthcheck;

import io.github.xinfra.lab.gateway.loadbalance.UpstreamTarget;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;

public class PassiveHealthCheckTest {

    private HealthChecker healthChecker;

    @Before
    public void setup() {
        HealthChecker.Config config = new HealthChecker.Config();
        config.setEnabled(true);
        config.setUnhealthyThreshold(3);
        config.setHealthyThreshold(2);
        healthChecker = new HealthChecker(config, () -> Flux.empty());
    }

    @Test
    public void testPassiveFailureMarksUnhealthy() {
        UpstreamTarget target = new UpstreamTarget("http://localhost:8080", 1);
        Assert.assertTrue(target.isHealthy());

        healthChecker.recordResult(target, false);
        healthChecker.recordResult(target, false);
        Assert.assertTrue(target.isHealthy());

        healthChecker.recordResult(target, false);
        Assert.assertFalse(target.isHealthy());
    }

    @Test
    public void testPassiveSuccessRecovery() {
        UpstreamTarget target = new UpstreamTarget("http://localhost:8080", 1);

        for (int i = 0; i < 3; i++) {
            healthChecker.recordResult(target, false);
        }
        Assert.assertFalse(target.isHealthy());

        healthChecker.recordResult(target, true);
        Assert.assertFalse(target.isHealthy());

        healthChecker.recordResult(target, true);
        Assert.assertTrue(target.isHealthy());
    }

    @Test
    public void testSuccessResetsFailureCount() {
        UpstreamTarget target = new UpstreamTarget("http://localhost:8080", 1);

        healthChecker.recordResult(target, false);
        healthChecker.recordResult(target, false);
        healthChecker.recordResult(target, true);
        healthChecker.recordResult(target, false);
        healthChecker.recordResult(target, false);

        Assert.assertTrue(target.isHealthy());
    }

    @Test
    public void testDisabledHealthCheckerSkipsRecording() {
        HealthChecker.Config disabledConfig = new HealthChecker.Config();
        disabledConfig.setEnabled(false);
        disabledConfig.setUnhealthyThreshold(1);
        HealthChecker disabled = new HealthChecker(disabledConfig, () -> Flux.empty());

        UpstreamTarget target = new UpstreamTarget("http://localhost:8080", 1);
        disabled.recordResult(target, false);
        Assert.assertTrue(target.isHealthy());
    }

    @Test
    public void testStaticInstanceAccessor() {
        HealthChecker.setInstance(healthChecker);
        Assert.assertSame(healthChecker, HealthChecker.getInstance());
        HealthChecker.setInstance(null);
    }
}
