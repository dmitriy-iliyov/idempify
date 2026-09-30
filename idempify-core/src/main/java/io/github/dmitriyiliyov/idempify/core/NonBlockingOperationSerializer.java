package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;

import java.util.Objects;

public class NonBlockingOperationSerializer implements OperationSerializer<NonBlockingOperation, RawNonBlockingOperation> {

    private final ResultSerializer resultSerializer;
    private final ResponseSerializer responseSerializer;

    public NonBlockingOperationSerializer(ResultSerializer resultSerializer, ResponseSerializer responseSerializer) {
        this.resultSerializer = Objects.requireNonNull(resultSerializer, "resultSerializer cannot be null");
        this.responseSerializer = Objects.requireNonNull(responseSerializer, "responseSerializer cannot be null");
    }

    @Override
    public RawNonBlockingOperation serialize(NonBlockingOperation operation) {
        if (operation == null) {
            return null;
        }

        String exceptionClassName = null;
        if (OperationStatus.EXCEPTIONALLY_PROCESSED.equals(operation.getStatus())) {
            exceptionClassName = operation.getResult().getClass().getName();
        }

        return new RawNonBlockingOperation(
                operation.getIdempotencyKey(),
                operation.getStatus(),
                operation.isFirstAttempt(),
                operation.getResult() == null ? null : resultSerializer.serialize(operation.getResult()),
                exceptionClassName,
                operation.getResponse() == null ? null : responseSerializer.serialize(operation.getResponse()),
                operation.getFingerprint(),
                operation.getRemainingMillis(),
                operation.getCreatedAt(),
                operation.getLockToken(),
                operation.getRemainingLockMillis()
        );
    }
}
