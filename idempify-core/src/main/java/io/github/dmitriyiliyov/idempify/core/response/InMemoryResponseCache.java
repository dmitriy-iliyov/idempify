package io.github.dmitriyiliyov.idempify.core.response;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class InMemoryResponseCache implements ResponseCache {

    private final Map<UUID, CacheRecord> cache;
    private final Clock clock;
    private final Object lock = new Object();

    public InMemoryResponseCache(int capacity, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity cannot be ZERO or negative");
        }
        this.cache = new BoundedRecords(capacity);
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        synchronized (lock) {
            CacheRecord r = cache.get(idempotencyKey);

            if (r == null) {
                return null;
            }

            if (clock.instant().isBefore(r.expiresAt())) {
                return r.response();
            }
            cache.remove(idempotencyKey, r);
            return null;
        }
    }

    @Override
    public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) {
        if (argumentsAreInvalid(idempotencyKey, response, ttl)) {
            return;
        }
        CacheRecord r = new CacheRecord(response, clock.instant().plus(ttl));
        synchronized (lock) {
            cache.remove(idempotencyKey);
            cache.put(idempotencyKey, r);
        }
    }

    private boolean argumentsAreInvalid(UUID idempotencyKey, CachedResponse response, Duration ttl) {
        return idempotencyKey == null || response == null || ttl == null || ttl.isNegative() || ttl.isZero();
    }

    private static final class BoundedRecords extends LinkedHashMap<UUID, CacheRecord> {

        private final int capacity;

        private BoundedRecords(int capacity) {
            super(capacity + 1, 1.0f, false);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, CacheRecord> eldest) {
            return size() > capacity;
        }
    }

    private record CacheRecord(
            CachedResponse response,
            Instant expiresAt
    ) {}
}
