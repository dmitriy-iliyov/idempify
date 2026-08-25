package io.github.dmitriyiliyov.idempify.core.response;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public abstract class AbstractResponseCacheDecorator implements ResponseCache {

    private final ResponseCache delegate;

    public AbstractResponseCacheDecorator(ResponseCache delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
        return delegate.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) {
        delegate.save(idempotencyKey, response, ttl);
    }
}
