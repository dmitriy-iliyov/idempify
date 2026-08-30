package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DefaultOperationDetail<T> implements OperationDetail<T> {

    private final UUID idempotencyKey;
    private final OperationStatus status;
    private final boolean replayed;
    private final T result;
    private final Instant expiresAt;

    public DefaultOperationDetail(UUID idempotencyKey,
                                  OperationStatus status,
                                  boolean replayed,
                                  T result,
                                  Instant expiresAt) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.replayed = replayed;
        this.result = result;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public OperationStatus getStatus() {
        return status;
    }

    @Override
    public boolean replayed() {
        return replayed;
    }

    @Override
    public T getResult() {
        return result;
    }

    @Override
    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "DefaultOperationDetail{" +
                "idempotencyKey=" + idempotencyKey +
                ", status=" + status +
                ", replayed=" + replayed +
                ", result=" + result +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
