package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.Objects;
import java.util.UUID;

public final class DefaultConflictContext<T> implements ConflictContext<T> {

    private final UUID idempotencyKey;
    private final Class<T> operationResultType;

    public DefaultConflictContext(UUID idempotencyKey, Class<T> operationResultType) {
        this.idempotencyKey = idempotencyKey;
        this.operationResultType = operationResultType;
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public Class<T> getOperationResultType() {
        return operationResultType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultConflictContext<?> that)) {
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
