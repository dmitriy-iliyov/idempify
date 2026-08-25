package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;

import java.time.Duration;

/**
 * What one call site asked for, exactly as its {@link Idempotent} spelled it - the most specific of the
 * configuration layers and the only one written next to the code it governs.
 * <p>
 * Every getter may answer "not specified here", each in the way its type allows: {@code null} for a value,
 * {@link Toggle#UNSELECTED} for a switch, {@link ProcessorTypeToggle#UNSELECTED} and
 * {@link ConflictHandleStrategyToggle#UNSELECTED} for the two choices. {@link OperationMetadataManager} layers a
 * named config and the global properties underneath and turns the result into an {@link OperationMetadata},
 * where nothing is left undecided.
 */
public interface RawOperationMetadata {

    /**
     * Returns the name of the header the idempotency key is read from.
     */
    String getHeaderName();

    /**
     * Returns how long this call site's completed result stays replayable.
     */
    Duration getTtl();

    /**
     * Returns which {@link IdempotentProcessor} runs this operation.
     */
    ProcessorTypeToggle getProcessorType();

    /**
     * Returns what to do when another request is already processing the same key.
     */
    ConflictHandleStrategyToggle getConflictHandleStrategy();

    /**
     * Returns whether a duplicate call must match the original request's fingerprint before its result is
     * replayed.
     */
    Toggle getFingerprintToggle();

    /**
     * Returns whether completed responses of this call site are copied into the cache in front of the
     * repository.
     */
    Toggle getCacheToggle();

    /**
     * Returns whether a client-error response is worth caching - it is a real answer of the operation, but
     * one the client is likely to stop repeating.
     */
    Toggle getCache4xxToggle();

    /**
     * Returns whether a server-error response is worth caching - caching it replays the failure for the
     * whole TTL instead of letting a retry find the service recovered.
     */
    Toggle getCache5xxToggle();
}
