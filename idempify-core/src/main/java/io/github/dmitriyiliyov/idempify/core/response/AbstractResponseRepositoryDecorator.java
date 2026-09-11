package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractResponseRepositoryDecorator implements ResponseRepository {

    private final ResponseRepository delegate;

    public AbstractResponseRepositoryDecorator(ResponseRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public RawResponseContainer save(UUID idempotencyKey, String response) {
        return delegate.save(idempotencyKey, response);
    }

    @Override
    public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
        return delegate.findByIdempotencyKey(idempotencyKey);
    }
}
