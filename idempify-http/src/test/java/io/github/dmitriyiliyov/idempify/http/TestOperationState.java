package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.response.OperationState;

import java.time.Instant;

/**
 * Stands in for whatever core publishes into the channel: http reads nothing from it but the two values below.
 */
final class TestOperationState implements OperationState {

    private final Instant expiresAt;
    private final boolean replayed;

    private TestOperationState(Instant expiresAt, boolean replayed) {
        this.expiresAt = expiresAt;
        this.replayed = replayed;
    }

    static TestOperationState of(Instant expiresAt, boolean replayed) {
        return new TestOperationState(expiresAt, replayed);
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
