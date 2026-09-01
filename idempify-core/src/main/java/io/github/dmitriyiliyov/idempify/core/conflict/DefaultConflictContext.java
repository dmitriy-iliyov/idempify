package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.ResultType;

import java.util.Objects;
import java.util.UUID;

public final class DefaultConflictContext implements ConflictContext {

    private final UUID idempotencyKey;
    private final ResultType operationResultType;

    public DefaultConflictContext(UUID idempotencyKey, ResultType operationResultType) {
        this.idempotencyKey = idempotencyKey;
        this.operationResultType = operationResultType;
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public ResultType getOperationResultType() {
        return operationResultType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultConflictContext that)) {
            return false;
        }
        return Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(operationResultType, that.operationResultType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, operationResultType);
    }

    @Override
    public String toString() {
        return "DefaultConflictContext{" +
                "idempotencyKey=" + idempotencyKey +
                ", operationResultType=" + operationResultType +
                '}';
    }
}
