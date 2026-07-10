package io.github.xinfra.lab.gateway.ratelimit;

import org.junit.Assert;
import org.junit.Test;

public class SlidingWindowRateLimiterTest {

    @Test
    public void testAllowsWithinLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        for (int i = 0; i < 5; i++) {
            RateLimiter.Response response = limiter.isAllowed("key1", 10, 5).block();
            Assert.assertTrue("Request " + i + " should be allowed", response.isAllowed());
        }
    }

    @Test
    public void testRejectsOverLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.isAllowed("key1", 10, 5).block();
        }
        RateLimiter.Response response = limiter.isAllowed("key1", 10, 5).block();
        Assert.assertFalse("Request over limit should be rejected", response.isAllowed());
        Assert.assertTrue(response.getRetryAfterMs() > 0);
    }

    @Test
    public void testDifferentKeysIndependent() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        for (int i = 0; i < 3; i++) {
            limiter.isAllowed("key1", 10, 3).block();
        }
        RateLimiter.Response response = limiter.isAllowed("key2", 10, 3).block();
        Assert.assertTrue("Different key should be allowed", response.isAllowed());
    }

    @Test
    public void testWindowExpiry() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        for (int i = 0; i < 3; i++) {
            limiter.isAllowed("key1", 10, 3).block();
        }
        RateLimiter.Response rejected = limiter.isAllowed("key1", 10, 3).block();
        Assert.assertFalse(rejected.isAllowed());

        Thread.sleep(1100);

        RateLimiter.Response afterExpiry = limiter.isAllowed("key1", 10, 3).block();
        Assert.assertTrue("After window expiry should be allowed", afterExpiry.isAllowed());
    }

    @Test
    public void testRemainingCount() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        RateLimiter.Response response = limiter.isAllowed("key1", 10, 5).block();
        Assert.assertTrue(response.isAllowed());
        Assert.assertEquals(4, response.getRemaining());
    }
}
