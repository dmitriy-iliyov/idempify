package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Describes the single call being processed: what identifies it, what it returns, and how to run it.
 * <p>
 * Where {@link OperationMetadata} carries the settings that hold for a whole call site, this carries the
 * values that differ from one call to the next.
 *
 * @param <T> the type of the result.
 */
public interface OperationContext<T> {

    /**
     * Returns the type a stored result is deserialized back into when a duplicate call is replayed.
     */
    Class<T> getOperationResultType();

    /**
     * Returns the caller's business operation, to be run only when this call is the first attempt.
     */
    ExternalOperationCallback<T> getOperationCallback();

    /**
     * Returns the key this call is deduplicated by.
     */
    UUID getIdempotencyKey();

    /**
     * Returns the fingerprint of the current request, or {@link Optional#empty()} if fingerprinting is off
     * for this call.
     * <p>
     * A present fingerprint is never blank: whoever builds the context is expected to reject an empty one
     * rather than pass it on, so a caller that has one may compare it as is.
     */
    Optional<String> getFingerprint();
}
