package io.github.xinfra.lab.gateway.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class RedisRateLimiter implements RateLimiter {

    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local rate = tonumber(ARGV[1])\n" +
            "local capacity = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local tokens_key = key .. '.tokens'\n" +
            "local timestamp_key = key .. '.ts'\n" +
            "local current_tokens = tonumber(redis.call('get', tokens_key))\n" +
            "local last_time = tonumber(redis.call('get', timestamp_key))\n" +
            "if current_tokens == nil then\n" +
            "  current_tokens = capacity\n" +
            "  last_time = now\n" +
            "end\n" +
            "local elapsed = math.max(0, now - last_time)\n" +
            "local fill = math.min(capacity, current_tokens + (elapsed * rate / 1000))\n" +
            "local allowed = 0\n" +
            "local new_tokens = fill\n" +
            "if fill >= 1 then\n" +
            "  new_tokens = fill - 1\n" +
            "  allowed = 1\n" +
            "end\n" +
            "local ttl = math.ceil(capacity / rate) + 1\n" +
            "redis.call('setex', tokens_key, ttl, tostring(new_tokens))\n" +
            "redis.call('setex', timestamp_key, ttl, tostring(now))\n" +
            "return {allowed, math.floor(new_tokens)}";

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisReactiveCommands<String, String> commands;
    private final String keyPrefix;

    public RedisRateLimiter(String redisUri) {
        this(redisUri, "rate_limit:");
    }

    public RedisRateLimiter(String redisUri, String keyPrefix) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.commands = connection.reactive();
        this.keyPrefix = keyPrefix;
        log.info("RedisRateLimiter connected to {}", redisUri);
    }

    RedisRateLimiter(RedisReactiveCommands<String, String> commands, String keyPrefix) {
        this.redisClient = null;
        this.connection = null;
        this.commands = commands;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public Mono<Response> isAllowed(String key, long replenishRate, long burstCapacity) {
        String redisKey = keyPrefix + key;
        long nowMs = System.currentTimeMillis();

        String[] keys = {redisKey};
        String[] args = {
                String.valueOf(replenishRate),
                String.valueOf(burstCapacity),
                String.valueOf(nowMs)
        };

        return commands.<Long>eval(LUA_SCRIPT, ScriptOutputType.MULTI, keys, args)
                .collectList()
                .map(results -> {
                    long allowed = results.get(0);
                    long remaining = results.get(1);
                    if (allowed == 1) {
                        return new Response(true, remaining, 0);
                    }
                    long retryAfterMs = 1000L / Math.max(1, replenishRate);
                    return new Response(false, 0, retryAfterMs);
                })
                .onErrorResume(e -> {
                    log.error("Redis rate limit check failed, allowing request", e);
                    return Mono.just(new Response(true, burstCapacity, 0));
                });
    }

    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}
