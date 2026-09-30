package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerResultException;

import java.time.Duration;
import java.util.UUID;

/**
 * Manages the lifecycle of an idempotent operation whose claim is a record of its own, kept <em>outside</em>
 * the business transaction. Nothing for a duplicate to block on follows from that: it meets the unfinished
 * operation instead of waiting for it, which is where a {@code ConflictHandler} comes in.
 * <p>
 * Being outside the business transaction, the record also outlives a rollback.
 * <p>
 * The claim is a lease: it carries a token of its own, every way out of it is conditional on that token, and
 * the store counts its length from the call site's lock TTL. A holder that died therefore keeps the key until
 * the lease runs out, not until the record's TTL.
 *
 * @see BlockingOperationManager
 */
public interface NonBlockingOperationManager {
    LockDetail tryLock(OperationContext context, OperationMetadata metadata);

    /**
     * Reads the operation behind the key, checks the call against it and settles a conflict, so that the caller
     * is left with one decision: run the business method or replay the outcome.
     * <p>
     * A call holding the claim gets the record it claimed; a call that met another call's claim gets a conflict,
     * which is handed to the call site's {@code ConflictHandler}, and the completed operation it returns takes the
     * conflict's place. When the call site uses a fingerprint, the request's one is matched against the
     * operation's, and a mismatch is answered by the call site's fingerprint policy. Whatever the policy or the
     * handler throws to refuse the call propagates as is.
     *
     * @param context the call being checked.
     * @param metadata the resolved settings of the call site, its fingerprint policy and conflict handler among them.
     * @param lockDetail what {@link #tryLock} returned for this call.
     * @return the detail the caller acts on. Its status is never {@link OperationStatus#CONFLICT}:
     *         {@link OperationStatus#IN_PROCESS} for an operation this call holds and has to run,
     *         {@link OperationStatus#PROCESSED} or {@link OperationStatus#EXCEPTIONALLY_PROCESSED} for an outcome
     *         to replay.
     * @throws ConflictHandlerResultException if the conflict handler returns {@code null} or an operation that
     *         has not completed.
     */
    NonBlockingOperationDetail fetchAndValidate(OperationContext context,
                                                OperationMetadata metadata,
                                                LockDetail lockDetail);

    NonBlockingOperationDetail complete(NonBlockingOperationDetail detail, Object result, Duration ttl);

    void rollback(UUID idempotencyKey, UUID lockToken);

    void completeExceptionally(OperationContext context, Throwable t, Duration ttl, UUID lockToken);

    void unlock(UUID idempotencyKey, UUID lockToken);
}
