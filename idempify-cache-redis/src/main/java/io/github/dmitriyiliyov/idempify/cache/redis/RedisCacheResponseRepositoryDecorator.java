package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.response.AbstractResponseRepositoryDecorator;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps recently replayed responses in redis in front of the store.
 * <p>
 * A miss never changes the answer: everything not served from redis is asked of the delegate, so an
 * unavailable or empty cache changes how fast a replay is answered and nothing about what it answers.
 */
public class RedisCacheResponseRepositoryDecorator extends AbstractResponseRepositoryDecorator {

    private static final String KEY_TEMPLATE = "%s:%s";
    private final RedisTemplate<String, RawResponseContainer> redisTemplate;
    private final String cacheName;
    private final CacheEventListener listener;
    private final Clock clock;

    public RedisCacheResponseRepositoryDecorator(ResponseRepository repository,
                                                 RedisTemplate<String, RawResponseContainer> redisTemplate,
                                                 String cacheName,
                                                 CacheEventListener listener,
                                                 Clock clock) {
        super(repository);
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.cacheName = Objects.requireNonNull(cacheName, "cacheName cannot be null");
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public RawResponseContainer save(UUID idempotencyKey, String response) {
        RawResponseContainer saved = super.save(idempotencyKey, response);
        cache(idempotencyKey, saved);
        return saved;
    }

    @Override
    public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }

        Optional<RawResponseContainer> container = Optional.ofNullable(
                redisTemplate.opsForValue().get(formatKey(idempotencyKey))
        );

        if (container.isEmpty()) {
            listener.onMiss();
            container = super.findByIdempotencyKey(idempotencyKey);
            container.ifPresent(value -> cache(idempotencyKey, value));
        } else {
            listener.onHit();
        }

        return container;
    }

    private void cache(UUID idempotencyKey, RawResponseContainer container) {
        if (idempotencyKey == null || container.getResponse() == null || container.getExpiresAt() == null) {
            return;
        }

        Duration ttl = Duration.between(clock.instant(), container.getExpiresAt());
        if (!ttl.isPositive()) {
            return;
        }

        redisTemplate.opsForValue().set(formatKey(idempotencyKey), container, ttl);
    }

    private String formatKey(UUID idempotencyKey) {
        return KEY_TEMPLATE.formatted(cacheName, idempotencyKey);
    }
}
