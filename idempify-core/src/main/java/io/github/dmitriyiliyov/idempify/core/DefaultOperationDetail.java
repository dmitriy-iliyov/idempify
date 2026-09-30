package io.github.dmitriyiliyov.idempify.core;

import java.util.Objects;
import java.util.UUID;

public class DefaultOperationDetail implements OperationDetail {

    private final UUID idempotencyKey;
    private final OperationStatus status;
    private final boolean shouldRecordResponse;
    private final Object result;

    public DefaultOperationDetail(UUID idempotencyKey,
                                  OperationStatus status,
                                  Object result,
                                  boolean shouldRecordResponse) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.result = result;
        this.shouldRecordResponse = shouldRecordResponse;
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
    public boolean shouldRecordResponse() {
        return shouldRecordResponse;
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
                ", shouldRecordResponse=" + shouldRecordResponse +
                ", result=" + result +
                '}';
    }
}
