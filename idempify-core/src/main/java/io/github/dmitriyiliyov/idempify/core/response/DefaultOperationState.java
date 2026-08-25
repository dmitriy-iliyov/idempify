package io.github.dmitriyiliyov.idempify.core.response;

import java.time.Instant;
import java.util.Objects;

public final class DefaultOperationState implements OperationState {

    private final Instant expiresAt;
    private final boolean replayed;

    public DefaultOperationState(Instant expiresAt, Boolean replayed) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        this.replayed = Objects.requireNonNull(replayed, "replayed cannot be null");
    }

    @Override
    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean replayed() {
        return replayed;
    }
}
