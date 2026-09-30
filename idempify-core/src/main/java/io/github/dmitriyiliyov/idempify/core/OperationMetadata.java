package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
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
     * takes the key from an expression instead.
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

    Duration getTtl();

    /**
     * Returns how long a claim on the key stays held, or {@code null} when the claim has no lifetime of
     * its own: under {@code BLOCKING} it lives and dies with the business transaction.
     */
    Duration getLockTtl();

    /**
     * Returns which {@code IdempotentProcessor} runs this operation - under {@code BLOCKING} a duplicate waits
     * on the first call's claim, under {@code NON_BLOCKING} it meets the unfinished operation instead.
     * {@code DelegatingIdempotentProcessor} dispatches on this value and fails when no processor serves it.
     */
    ProcessorType getProcessorType();

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
     * fingerprint at all. Callers guard on {@link #useFingerprint()} first.
     */
    FingerprintPolicy getFingerprintPolicy();

    ResponseConfig getResponseConfig();
}
