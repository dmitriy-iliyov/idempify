package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.response.AbstractResponseRepositoryDecorator;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Keeps recently replayed responses in Redis in front of the store.
 * <p>
 * A miss never changes the answer: everything not served from Redis is asked of the delegate, so an empty
 * cache changes how fast a replay is answered and nothing about what it answers.
 * <p>
 * A failure of Redis is not a miss: both methods let it out, and a caller that does not swallow it turns an
 * outage of the accelerator into a failed request. What such a caller swallows is wider than Redis - a
 * failure of the store behind this decorator arrives as the same {@code DataAccessException}.
 * <p>
 * The time a hit has left is the key's own TTL, read together with the value, not the one stored inside it:
 * the stored container carries what was left when it was written, and handing that back would overstate the
 * record's life by however long the key has been sitting here.
 */
public class RedisCacheResponseRepositoryDecorator extends AbstractResponseRepositoryDecorator {

    private static final String KEY_TEMPLATE = "%s:%s";
    private final RedisTemplate<String, RawResponseContainer> redisTemplate;
    private final String cacheName;
    private final CacheEventListener listener;

    public RedisCacheResponseRepositoryDecorator(ResponseRepository repository,
                                                 RedisTemplate<String, RawResponseContainer> redisTemplate,
                                                 String cacheName,
                                                 CacheEventListener listener) {
        super(repository);
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
        this.cacheName = Objects.requireNonNull(cacheName, "cacheName cannot be null");
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
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

        Optional<RawResponseContainer> container = findCached(formatKey(idempotencyKey));

        if (container.isEmpty()) {
            listener.onMiss();
            container = super.findByIdempotencyKey(idempotencyKey);
            container.ifPresent(value -> cache(idempotencyKey, value));
        } else {
            listener.onHit();
        }

        return container;
    }

    /**
     * A key that expired between the two reads answers {@code -2} for its TTL, and one without a TTL {@code -1}
     * - neither is served, so both fall through to the store like any other miss.
     */
    private Optional<RawResponseContainer> findCached(String key) {
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @SuppressWarnings("unchecked")
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                RedisOperations<String, RawResponseContainer> typed =
                        (RedisOperations<String, RawResponseContainer>) operations;
                typed.opsForValue().get(key);
                typed.getExpire(key, TimeUnit.MILLISECONDS);
                return null;
            }
        });

        RawResponseContainer cached = (RawResponseContainer) results.get(0);
        Number remainingMillis = (Number) results.get(1);
        if (cached == null || remainingMillis == null || remainingMillis.longValue() <= 0) {
            return Optional.empty();
        }

        return Optional.of(
                new DefaultRawResponseContainer(
                        cached.getResponse(),
                        cached.getFingerprint(),
                        remainingMillis.longValue()
                )
        );
    }

    private void cache(UUID idempotencyKey, RawResponseContainer container) {
        if (idempotencyKey == null || container.getResponse() == null || container.getRemainingMillis() == null) {
            return;
        }

        Duration ttl = Duration.ofMillis(container.getRemainingMillis());
        if (!ttl.isPositive()) {
            return;
        }

        redisTemplate.opsForValue().set(formatKey(idempotencyKey), container, ttl);
    }

    private String formatKey(UUID idempotencyKey) {
        return KEY_TEMPLATE.formatted(cacheName, idempotencyKey);
    }
}
