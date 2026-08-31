package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

public class DefaultOperationMapper implements OperationMapper {

    @Override
    public Operation toOperation(UUID idempotencyKey, String fingerprint, OperationMetadata metadata, Instant timestamp) {
        return new Operation(
                idempotencyKey,
                OperationStatus.IN_PROCESS,
                true,
                null,
                fingerprint,
                null,
                timestamp
        );
    }
}
