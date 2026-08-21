package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class RedisResponseCache implements ResponseCache {

    private static final String KEY_TEMPLATE = "%s:%s";
    private final RedisTemplate<String, CachedResponse> redisTemplate;
    private final String cacheName;

    public RedisResponseCache(RedisTemplate<String, CachedResponse> redisTemplate, String cacheName) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.cacheName = Objects.requireNonNull(cacheName, "cacheName cannot be null");
    }

    @Override
    public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        return redisTemplate.opsForValue().get(formatKey(idempotencyKey));
    }

    @Override
    public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) {
        if (idempotencyKey == null || response == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(formatKey(idempotencyKey), response, ttl);
    }

    private String formatKey(UUID idempotencyKey) {
        return KEY_TEMPLATE.formatted(cacheName, idempotencyKey);
    }
}
