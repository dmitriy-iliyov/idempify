package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.util.Objects;

public class DefaultOperationDeserializer implements OperationDeserializer {

    private final ResultDeserializer resultDeserializer;
    private final ResponseDeserializer responseDeserializer;

    public DefaultOperationDeserializer(ResultDeserializer resultDeserializer,
                                        ResponseDeserializer responseDeserializer) {
        this.resultDeserializer = Objects.requireNonNull(resultDeserializer, "resultDeserializer cannot be null");
        this.responseDeserializer = Objects.requireNonNull(responseDeserializer, "responseDeserializer cannot be null");
    }

    @Override
    public Operation deserialize(RawOperation operation, ResultType resultType) {
        if (operation == null) {
            return null;
        }

        return new Operation(
                operation.idempotencyKey(),
                operation.status(),
                operation.isFirstAttempt(),
                operation.result() == null || resultType == null
                        ? null
                        : resultDeserializer.deserialize(operation.result(), resultType),
                resultType,
                operation.response() == null ? null : responseDeserializer.deserialize(operation.response()),
                operation.fingerprint(),
                operation.expiresAt(),
                operation.createdAt()
        );
    }
}
