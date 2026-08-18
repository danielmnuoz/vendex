package com.vendex.gateway.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class RedisSlidingWindowRateLimiter {

    private static final String SCRIPT = """
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
              local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
              local retry = window
              if oldest[2] then
                retry = math.max(1, window - (now - tonumber(oldest[2])))
              end
              redis.call('PEXPIRE', KEYS[1], window)
              return {0, 0, retry}
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], window)
            return {1, limit - count - 1, 0}
            """;

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final DefaultRedisScript<List> script = new DefaultRedisScript<>(SCRIPT, List.class);

    public RedisSlidingWindowRateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public Decision evaluate(String identity, int limit, Duration window) {
        long now = clock.millis();
        long windowMs = window.toMillis();
        List<?> result = redis.execute(
                script,
                List.of("vendex:gateway:rate:" + identity),
                Long.toString(now),
                Long.toString(windowMs),
                Integer.toString(limit),
                now + ":" + UUID.randomUUID());
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Redis returned an invalid rate-limit result");
        }
        return new Decision(
                number(result.get(0)) == 1,
                number(result.get(1)),
                Duration.ofMillis(number(result.get(2))));
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public record Decision(boolean allowed, long remaining, Duration retryAfter) {}
}
