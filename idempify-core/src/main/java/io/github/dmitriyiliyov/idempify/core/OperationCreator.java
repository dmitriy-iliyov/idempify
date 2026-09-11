package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;

/**
 * Builds the row that represents a call in the store, from the values that identify the call.
 */
public interface OperationCreator {

    /**
     * Maps a call to the operation to be persisted for it - a claim, not a finished operation: status
     * {@link OperationStatus#IN_PROCESS}, no result, and no expiry. The expiry is counted from completion,
     * so nothing here can know it yet.
     *
     * @param context   everything known about the call: the key it is deduplicated by, the type its result
     *                  will have, and the fingerprint of the request when fingerprinting is on.
     * @param timestamp the current instant, used as the creation time.
     * @return the operation to persist.
     */
    Operation create(OperationContext context, Instant timestamp);
}
