package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

public class DefaultOperationMapper implements OperationMapper {

    @Override
    public Operation toOperation(UUID idempotencyKey, String fingerprint, OperationMetadata metadata, Instant timestamp) {
        Instant expiresAt = timestamp.plus(metadata.getTtl());
        return new Operation(
                idempotencyKey,
                OperationStatus.IN_PROCESS,
                true,
                null,
                fingerprint,
                expiresAt,
                timestamp
        );
    }
}
