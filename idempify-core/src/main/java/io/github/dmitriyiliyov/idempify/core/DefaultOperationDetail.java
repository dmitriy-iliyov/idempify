package io.github.dmitriyiliyov.idempify.core;

import java.util.Objects;
import java.util.UUID;

public final class DefaultOperationDetail implements OperationDetail {

    private final UUID idempotencyKey;
    private final OperationStatus status;
    private final boolean replayed;
    private final Object result;

    public DefaultOperationDetail(UUID idempotencyKey,
                                  OperationStatus status,
                                  boolean replayed,
                                  Object result) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.replayed = replayed;
        this.result = result;
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
    public Object getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "DefaultOperationDetail{" +
                "idempotencyKey=" + idempotencyKey +
                ", status=" + status +
                ", replayed=" + replayed +
                ", result=" + result +
                '}';
    }
}
