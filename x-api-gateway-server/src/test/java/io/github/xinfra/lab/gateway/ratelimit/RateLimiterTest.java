package io.github.xinfra.lab.gateway.ratelimit;

import org.junit.Assert;
import org.junit.Test;

public class RateLimiterTest {

    @Test
    public void testLocalRateLimiterAllowed() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        RateLimiter.Response response = limiter.isAllowed("test-key", 100, 10).block();
        Assert.assertNotNull(response);
        Assert.assertTrue(response.isAllowed());
        Assert.assertTrue(response.getRemaining() >= 0);
    }

    @Test
    public void testLocalRateLimiterBurstExhausted() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.isAllowed("exhaust-key", 1, 5).block();
        }
        RateLimiter.Response response = limiter.isAllowed("exhaust-key", 1, 5).block();
        Assert.assertNotNull(response);
        Assert.assertFalse(response.isAllowed());
        Assert.assertEquals(0, response.getRemaining());
        Assert.assertTrue(response.getRetryAfterMs() > 0);
    }

    @Test
    public void testLocalRateLimiterSeparateKeys() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        for (int i = 0; i < 3; i++) {
            limiter.isAllowed("key-a", 1, 3).block();
        }
        RateLimiter.Response exhausted = limiter.isAllowed("key-a", 1, 3).block();
        Assert.assertFalse(exhausted.isAllowed());

        RateLimiter.Response separate = limiter.isAllowed("key-b", 1, 3).block();
        Assert.assertTrue(separate.isAllowed());
    }

    @Test
    public void testResponseFields() {
        RateLimiter.Response allowed = new RateLimiter.Response(true, 5, 0);
        Assert.assertTrue(allowed.isAllowed());
        Assert.assertEquals(5, allowed.getRemaining());
        Assert.assertEquals(0, allowed.getRetryAfterMs());

        RateLimiter.Response denied = new RateLimiter.Response(false, 0, 1000);
        Assert.assertFalse(denied.isAllowed());
        Assert.assertEquals(0, denied.getRemaining());
        Assert.assertEquals(1000, denied.getRetryAfterMs());
    }

    @Test
    public void testLocalRateLimiterRemainingCount() {
        LocalRateLimiter limiter = new LocalRateLimiter();
        RateLimiter.Response first = limiter.isAllowed("count-key", 100, 5).block();
        Assert.assertTrue(first.isAllowed());
        Assert.assertEquals(4, first.getRemaining());

        RateLimiter.Response second = limiter.isAllowed("count-key", 100, 5).block();
        Assert.assertTrue(second.isAllowed());
        Assert.assertEquals(3, second.getRemaining());
    }
}
