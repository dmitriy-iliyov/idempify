package io.github.dmitriyiliyov.idempify.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DefaultOperationContext<T> implements OperationContext<T> {

    private final Class<T> operationResultType;
    private final ExternalOperationCallback<T> operationCallback;
    private final UUID idempotencyKey;
    private final String fingerprint;

    public DefaultOperationContext(Class<T> operationResultType,
                                   ExternalOperationCallback<T> operationCallback,
                                   UUID idempotencyKey,
                                   String fingerprint) {
        this.operationResultType = Objects.requireNonNull(operationResultType, "operationResultType cannot be null");
        this.operationCallback = Objects.requireNonNull(operationCallback, "operationCallback cannot be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        this.fingerprint = fingerprint;
    }

    @Override
    public Class<T> getOperationResultType() {
        return operationResultType;
    }

    @Override
    public ExternalOperationCallback<T> getOperationCallback() {
        return operationCallback;
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public Optional<String> getFingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultOperationContext<?> that)) {
            return false;
        }
        return Objects.equals(operationResultType, that.operationResultType)
                && Objects.equals(operationCallback, that.operationCallback)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(fingerprint, that.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationResultType, operationCallback, idempotencyKey, fingerprint);
    }

    @Override
    public String toString() {
        return "DefaultOperationContext{" +
                "operationResultType=" + operationResultType +
                ", operationCallback=" + operationCallback +
                ", idempotencyKey=" + idempotencyKey +
                ", fingerprint='" + fingerprint + '\'' +
                '}';
    }
}
