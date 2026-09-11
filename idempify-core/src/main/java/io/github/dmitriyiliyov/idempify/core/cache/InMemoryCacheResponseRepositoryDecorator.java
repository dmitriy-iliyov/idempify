package io.github.dmitriyiliyov.idempify.core.cache;

import io.github.dmitriyiliyov.idempify.core.response.AbstractResponseRepositoryDecorator;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

/**
 * Keeps recently replayed responses in front of the store, bounded by capacity and by the record's own expiry.
 * <p>
 * A miss never changes the answer: everything not served from here is asked of the delegate, so switching the
 * cache off changes how fast a replay is answered and nothing about what it answers.
 */
public class InMemoryCacheResponseRepositoryDecorator extends AbstractResponseRepositoryDecorator {

    private final Map<UUID, RawResponseContainer> cache;
    private final Clock clock;
    private final Object lock = new Object();
    private final CacheEventListener listener;

    public InMemoryCacheResponseRepositoryDecorator(ResponseRepository repository,
                                                    int capacity,
                                                    Clock clock,
                                                    CacheEventListener listener) {
        super(repository);
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity cannot be ZERO or negative");
        }
        this.cache = new BoundedContainers(capacity);
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
    }

    @Override
    public RawResponseContainer save(UUID idempotencyKey, String response) {
        RawResponseContainer saved = super.save(idempotencyKey, response);
        if (idempotencyKey != null) {
            synchronized (lock) {
                cache(idempotencyKey, saved);
            }
        }
        return saved;
    }

    @Override
    public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }

        synchronized (lock) {
            RawResponseContainer cached = cache.get(idempotencyKey);

            if (cached != null && clock.instant().isBefore(cached.getExpiresAt())) {
                listener.onHit();
                return Optional.of(cached);
            }

            if (cached != null) {
                cache.remove(idempotencyKey);
            }

            listener.onMiss();
            Optional<RawResponseContainer> container = super.findByIdempotencyKey(idempotencyKey);
            container.ifPresent(value -> cache(idempotencyKey, value));
            return container;
        }
    }

    private void cache(UUID idempotencyKey, RawResponseContainer container) {
        if (container.getResponse() == null || container.getExpiresAt() == null) {
            return;
        }

        if (!Duration.between(clock.instant(), container.getExpiresAt()).isPositive()) {
            return;
        }

        cache.remove(idempotencyKey);
        cache.put(idempotencyKey, container);
    }

    private static final class BoundedContainers extends LinkedHashMap<UUID, RawResponseContainer> {

        private final int capacity;

        private BoundedContainers(int capacity) {
            super(capacity + 1, 1.0f, false);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, RawResponseContainer> eldest) {
            return size() > capacity;
        }
    }
}
