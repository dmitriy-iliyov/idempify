package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerResultException;

import java.time.Duration;
import java.util.UUID;

/**
 * Manages the lifecycle of an idempotent operation whose claim is a record of its own, taken <em>outside</em>
 * any transaction. Nothing for a duplicate to block on follows from that: it meets the unfinished operation
 * instead of waiting for it, which is where a {@code ConflictHandler} comes in.
 * <p>
 * Being outside, the claim outlives a rollback of the business transaction. Whether the outcome is written in
 * that transaction or after it is the processor's choice: every method below writes in whatever transaction is
 * open around it.
 * <p>
 * The claim is a lease: it carries a token of its own, every way out of it is conditional on that token, and
 * the store counts its length from the call site's lock TTL. A holder that died therefore keeps the key until
 * the lease runs out, not until the record's TTL.
 *
 * @see BlockingOperationManager
 */
public interface NonBlockingOperationManager {

    /**
     * Offers a claim on the key under a fresh token, for the call site's lock TTL: the key is taken when no
     * record is stored under it, or when the stored one - whatever its status - is held by nobody.
     *
     * @return the outcome of the attempt, carrying the offered token whether or not the claim was taken.
     * @throws NullPointerException if the {@link LockTokenGenerator} issued no token.
     */
    LockDetail tryLock(OperationContext context, OperationMetadata metadata);

    /**
     * Reads the operation behind the key, checks the call against it and settles a conflict, so that the caller
     * is left with one decision: run the business method or replay the outcome.
     * <p>
     * A call holding the claim gets the record it claimed, and a finished record that has outlived its TTL is
     * claimed anew and comes back {@link OperationStatus#IN_PROCESS}; a call that met another call's claim gets a
     * conflict, which is handed to the call site's {@code ConflictHandler}, and the completed operation it returns
     * takes the conflict's place. When this call holds the claim and the call site uses a fingerprint, the
     * request's one is matched against the record's, and a mismatch is answered by the call site's fingerprint
     * policy. Whatever the policy or the handler throws to refuse the call propagates as is.
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

    /**
     * Records the result onto the operation this call holds and keeps it for {@code ttl}, counted by the store
     * from the moment of writing.
     *
     * @param detail the operation this call holds, as {@link #fetchAndValidate} returned it.
     * @param result what the business method returned; {@code null} is a result like any other.
     * @param ttl how long repeats are answered with this result.
     * @return the completed operation, which has a response for the transport to record.
     */
    NonBlockingOperationDetail complete(NonBlockingOperationDetail detail, Object result, Duration ttl);

    /**
     * Releases the key by removing the record this call holds, so that the next attempt runs as the first one.
     */
    void rollback(UUID idempotencyKey, UUID lockToken);

    /**
     * Keeps the failure on the record this call holds as {@link OperationStatus#EXCEPTIONALLY_PROCESSED} for
     * {@code ttl}, counted by the store from the moment of writing; until then every repeat is answered with it.
     *
     * @param context the call that failed.
     * @param t what the business method threw.
     * @param ttl how long repeats are answered with the failure.
     * @param lockToken the token this call holds the claim under.
     */
    void completeExceptionally(OperationContext context, Throwable t, Duration ttl, UUID lockToken);

    /**
     * Lets go of the claim this call holds without touching the record, so the next call takes the key at once
     * instead of waiting for the lease to run out.
     */
    void unlock(UUID idempotencyKey, UUID lockToken);
}
