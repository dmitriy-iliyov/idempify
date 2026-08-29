package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.ResponseCacheConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.time.Duration;

/**
 * The fully resolved settings of one call site - what {@link OperationMetadataManager} produces after
 * layering the call site, its named config and the global properties.
 * <p>
 * Unlike the partial configs it is built from, every getter here has an answer: by the time a processor sees
 * it, nothing is left to decide.
 */
public interface OperationMetadata {

    /**
     * Returns the name of the header the idempotency key is read from, or {@code null} when the call site
     * takes the key from an expression instead - the one setting a broader layer may not answer, since a
     * header nobody reads would send the transport looking for a key the call site computes itself.
     */
    String getHeaderName();

    /**
     * Whether the key arrives with the request rather than being computed from the call's own arguments. A
     * transport that reads the key before the method runs - the response cache filter above all - can only
     * work when this holds.
     */
    default boolean useHeaderName() {
        return !StringUtils.isBlank(getHeaderName());
    }

    /**
     * Returns how long a completed operation's result stays replayable.
     */
    Duration getTtl();

    /**
     * Returns which {@code IdempotentProcessor} runs this operation - {@code TRANSACTIONAL} keeps the
     * operation record in the business transaction, {@code LOCK_BASED} keeps it outside so a failed result
     * survives the rollback. {@code DelegatingIdempotentProcessor} dispatches on this value and fails when no
     * processor serves it.
     */
    ProcessorType getProcessorType();

    /**
     * Returns whether a concurrent duplicate of this call site is handled by a handler rather than left to the
     * processor - which is to say whether a handler was resolved at all. Always {@code false} under
     * {@code TRANSACTIONAL}: there the insert lock already settles the race, and a conflict config is
     * rejected outright.
     */
    default boolean useConflictHandler() {
        return getConflictHandler() != null;
    }

    /**
     * Returns the handler to invoke when another request is already processing the same key.
     */
    ConflictHandler getConflictHandler();

    /**
     * Returns whether a duplicate call must match the original request's fingerprint before its result is
     * replayed - which is to say whether a policy was resolved at all. The two cannot disagree: there is one
     * field behind them, so a call site either has a policy and fingerprints, or has neither.
     */
    default boolean useFingerprint() {
        return getFingerprintPolicy() != null;
    }

    /**
     * Returns the policy that computes and compares fingerprints, or {@code null} when this call site does not
     * fingerprint at all.
     * <p>
     * Callers guard on {@link #useFingerprint()} first; the {@code null} is deliberate rather than replaced by
     * a do-nothing policy, so that a missing guard fails at once instead of quietly reporting every duplicate
     * as a mismatch and carrying on.
     */
    FingerprintPolicy getFingerprintPolicy();

    /**
     * Returns which results are worth an entry in the cache in front of the repository.
     */
    ResponseCacheConfig getResponseCacheConfig();
}
