package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.util.Objects;

public class DefaultOperationDeserializer implements OperationDeserializer<RawOperation, Operation> {

    private final ResultDeserializer resultDeserializer;
    private final ResponseDeserializer responseDeserializer;

    public DefaultOperationDeserializer(ResultDeserializer resultDeserializer,
                                        ResponseDeserializer responseDeserializer) {
        this.resultDeserializer = Objects.requireNonNull(resultDeserializer, "resultDeserializer cannot be null");
        this.responseDeserializer = Objects.requireNonNull(responseDeserializer, "responseDeserializer cannot be null");
    }

    @Override
    public Operation deserialize(RawOperation rawOperation, ResultType resultType) {
        if (rawOperation == null) {
            return null;
        }

        return new Operation(
                rawOperation.idempotencyKey(),
                rawOperation.status(),
                rawOperation.isFirstAttempt(),
                rawOperation.result() == null || resultType == null
                        ? null
                        : resultDeserializer.deserialize(rawOperation.result(), resultType),
                resultType,
                rawOperation.response() == null ? null : responseDeserializer.deserialize(rawOperation.response()),
                rawOperation.fingerprint(),
                rawOperation.remainingMillis(),
                rawOperation.createdAt()
        );
    }
}
