package io.github.dmitriyiliyov.idempify.core;

import java.time.Duration;
import java.time.Instant;

public class DefaultOperationMapper implements OperationMapper {

    @Override
    public Operation toOperation(OperationMetadata metadata, Instant instant) {
        Duration ttl = Duration.of(metadata.getTtl(), metadata.getTimeUnit().toChronoUnit());
        Instant expiresAt = instant.plusSeconds(ttl.toSeconds());
        return new Operation(
                metadata.getIdempotencyKey(),
                OperationState.IN_PROCESS,
                true,
                null,
                metadata.getFingerprint(),
                expiresAt,
                instant
        );
    }
}
