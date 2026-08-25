package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the row that represents a call in the store, from the values that identify the call and the settings
 * that decide when it expires.
 */
public interface OperationMapper {

    /**
     * Maps a call to the operation to be persisted for it.
     *
     * @param idempotencyKey the key the call is deduplicated by.
     * @param fingerprint    the fingerprint of the request, or {@code null} if fingerprinting is off.
     * @param metadata       the resolved settings of the call site, read for the TTL.
     * @param timestamp      the current instant, used as the creation time and as the base for the TTL.
     * @return the operation to persist.
     */
    Operation toOperation(UUID idempotencyKey, String fingerprint, OperationMetadata metadata, Instant timestamp);
}
