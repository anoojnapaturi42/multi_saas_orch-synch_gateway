package com.example.gateway.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis-backed token bucket. One bucket is maintained per IntegrationApp id,
 * so multiple gateway instances share the same vendor quota.
 */
@Component
public class DistributedTokenBucketRateLimiter {
    private static final String LUA = """
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill = tonumber(ARGV[3])
            local period = tonumber(ARGV[4])
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
            local tokens = tonumber(state[1]) or capacity
            local updated = tonumber(state[2]) or now
            local elapsed = math.max(0, now - updated)
            tokens = math.min(capacity, tokens + (elapsed * refill / period))
            local allowed = 0
            local retryAfter = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              retryAfter = math.ceil((1 - tokens) * period / refill)
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated', now)
            redis.call('PEXPIRE', KEYS[1], math.max(period * 2, 60000))
            return {allowed, retryAfter}
            """;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script = new DefaultRedisScript<>(LUA, List.class);

    public DistributedTokenBucketRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public Duration acquire(String targetAppId, Map<String, Object> config) {
        long capacity = number(config, "capacity", 100);
        long refillTokens = number(config, "refillTokens", capacity);
        long refillPeriodMillis = number(config, "refillPeriodMillis", 60_000);
        if (capacity < 1 || refillTokens < 1 || refillPeriodMillis < 1) {
            throw new IllegalArgumentException("Invalid rate_limit_config for app " + targetAppId);
        }
        List<?> result = redis.execute(script, List.of("gateway:rate-limit:" + targetAppId),
                Long.toString(System.currentTimeMillis()), Long.toString(capacity),
                Long.toString(refillTokens), Long.toString(refillPeriodMillis));
        long retryAfter = result == null || result.size() < 2 ? 0 : ((Number) result.get(1)).longValue();
        return Duration.ofMillis(Math.max(0, retryAfter));
    }

    private long number(Map<String, Object> config, String key, long defaultValue) {
        Object value = config.get(key);
        return value instanceof Number n ? n.longValue() : defaultValue;
    }
}
