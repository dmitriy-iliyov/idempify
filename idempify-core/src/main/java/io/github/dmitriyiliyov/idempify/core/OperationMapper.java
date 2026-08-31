package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the row that represents a call in the store, from the values that identify the call.
 */
public interface OperationMapper {

    /**
     * Maps a call to the operation to be persisted for it - a claim, not a finished operation: status
     * {@link OperationStatus#IN_PROCESS}, no result, and no expiry. The expiry is counted from completion,
     * so nothing here can know it yet.
     *
     * @param idempotencyKey the key the call is deduplicated by.
     * @param fingerprint    the fingerprint of the request, or {@code null} if fingerprinting is off.
     * @param metadata       the resolved settings of the call site, for an implementation that puts more of
     *                       them into the row than the default one does.
     * @param timestamp      the current instant, used as the creation time.
     * @return the operation to persist.
     */
    Operation toOperation(UUID idempotencyKey, String fingerprint, OperationMetadata metadata, Instant timestamp);
}
