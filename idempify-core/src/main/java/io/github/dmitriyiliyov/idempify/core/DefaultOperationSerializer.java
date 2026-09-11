package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;

import java.util.Objects;

public class DefaultOperationSerializer implements OperationSerializer {

    private final ResultSerializer resultSerializer;
    private final ResponseSerializer responseSerializer;

    public DefaultOperationSerializer(ResultSerializer resultSerializer, ResponseSerializer responseSerializer) {
        this.resultSerializer = Objects.requireNonNull(resultSerializer, "resultSerializer cannot be null");
        this.responseSerializer = Objects.requireNonNull(responseSerializer, "responseSerializer cannot be null");
    }

    @Override
    public RawOperation serialize(Operation operation) {
        if (operation == null) {
            return null;
        }

        return new RawOperation(
                operation.getIdempotencyKey(),
                operation.getStatus(),
                operation.isFirstAttempt(),
                resultSerializer.serialize(operation.getResult()),
                responseSerializer.serialize(operation.getResponse()),
                operation.getFingerprint(),
                operation.getExpiresAt(),
                operation.getCreatedAt()
        );
    }
}
