package io.github.xinfra.lab.gateway.ratelimit;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class RedisRateLimiterTest {

    private RedisReactiveCommands<String, String> mockCommands;
    private RedisRateLimiter limiter;

    @Before
    public void setup() {
        mockCommands = mock(RedisReactiveCommands.class);
        limiter = new RedisRateLimiter(mockCommands, "test:");
    }

    @Test
    public void testAllowed() {
        when(mockCommands.eval(anyString(), eq(ScriptOutputType.MULTI),
                any(String[].class), any(String[].class)))
                .thenReturn(Flux.just(1L, 9L));

        RateLimiter.Response response = limiter.isAllowed("key1", 10, 10).block();

        Assert.assertNotNull(response);
        Assert.assertTrue(response.isAllowed());
        Assert.assertEquals(9, response.getRemaining());
        Assert.assertEquals(0, response.getRetryAfterMs());
    }

    @Test
    public void testDenied() {
        when(mockCommands.eval(anyString(), eq(ScriptOutputType.MULTI),
                any(String[].class), any(String[].class)))
                .thenReturn(Flux.just(0L, 0L));

        RateLimiter.Response response = limiter.isAllowed("key2", 10, 10).block();

        Assert.assertNotNull(response);
        Assert.assertFalse(response.isAllowed());
        Assert.assertEquals(0, response.getRemaining());
        Assert.assertTrue(response.getRetryAfterMs() > 0);
    }

    @Test
    public void testRedisErrorFallbackAllows() {
        when(mockCommands.eval(anyString(), eq(ScriptOutputType.MULTI),
                any(String[].class), any(String[].class)))
                .thenReturn(Flux.error(new RuntimeException("Redis connection refused")));

        RateLimiter.Response response = limiter.isAllowed("key3", 10, 50).block();

        Assert.assertNotNull(response);
        Assert.assertTrue(response.isAllowed());
        Assert.assertEquals(50, response.getRemaining());
    }

    @Test
    public void testKeyPrefix() {
        when(mockCommands.eval(anyString(), eq(ScriptOutputType.MULTI),
                any(String[].class), any(String[].class)))
                .thenAnswer(invocation -> {
                    String[] keys = invocation.getArgument(2);
                    Assert.assertEquals("test:my-key", keys[0]);
                    return Flux.just(1L, 5L);
                });

        limiter.isAllowed("my-key", 10, 10).block();

        verify(mockCommands).eval(anyString(), eq(ScriptOutputType.MULTI),
                any(String[].class), any(String[].class));
    }

    @Test
    public void testCloseWithNullFields() {
        RedisRateLimiter testLimiter = new RedisRateLimiter(mockCommands, "pfx:");
        testLimiter.close();
    }
}
