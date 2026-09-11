package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;

public class DefaultOperationCreator implements OperationCreator {

    @Override
    public Operation create(OperationContext context, Instant timestamp) {
        return new Operation(
                context.getIdempotencyKey(),
                OperationStatus.IN_PROCESS,
                true,
                null,
                context.getResultType(),
                null,
                context.getFingerprint().orElse(null),
                null,
                timestamp
        );
    }
}
