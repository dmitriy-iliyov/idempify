package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.util.Optional;
import java.util.UUID;

/**
 * Describes the single call being processed: what identifies it, what it returns, and how to run it.
 * <p>
 * Where {@link OperationMetadata} carries the settings that hold for a whole call site, this carries the
 * values that differ from one call to the next.
 */
public interface OperationContext {

    UUID getIdempotencyKey();

    ResultType getResultType();

    ExternalOperationCallback getCallback();

    /**
     * Returns the fingerprint of the current request, or {@link Optional#empty()} if fingerprinting is off
     * for this call.
     * <p>
     * A present fingerprint is never blank: whoever builds the context is expected to reject an empty one
     * rather than pass it on, so a caller that has one may compare it as is.
     */
    Optional<String> getFingerprint();
}
