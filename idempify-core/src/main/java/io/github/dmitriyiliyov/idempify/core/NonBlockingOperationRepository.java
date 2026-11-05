package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Write side of a store whose claim is a lease rather than a lock held by a transaction, which is what the
 * non-blocking processor relies on: a duplicate meets the claim instead of waiting on it.
 * <p>
 * {@link #lockOrSaveAndLock} takes the claim under the token the row carries, for a lease the store counts from
 * {@code lockTtlMillis} by its own clock, and every way out of the claim - completing, recording a failure,
 * releasing, deleting - is conditional on that token. Like the blocking side, it deals in raw rows only and
 * answers reads with the time the record and its claim have left rather than with stored moments.
 */
public interface NonBlockingOperationRepository {

    /**
     * Takes the claim on the key under {@code operation.lockToken()} for a lease of {@code lockTtlMillis}: inserts
     * the row as given when the key is free, or sets the token and the lease on the stored row - whatever its
     * status - when nobody holds it or its lease has run out, leaving the rest of that row as it was. A row already
     * held under this very token is claimed again, its lease counted anew. Atomic per key: of two calls racing for
     * a free key, exactly one takes it.
     *
     * @param operation the row to insert if the key is free, carrying the token to claim it under.
     * @param lockTtlMillis the length of the lease, counted by the store from the moment of the claim.
     * @return whether the claim was taken; {@code false} means a live claim of another call holds the key.
     */
    boolean lockOrSaveAndLock(RawNonBlockingOperation operation, long lockTtlMillis);

    /**
     * Returns the row recorded under this key, or {@link Optional#empty()} if the key was never claimed
     * or its record has since been removed. Absence is an ordinary answer here, not a failure.
     * <p>
     * Takes no claim. The row answers with what is left of its record, {@code null} for one without an expiry,
     * and of its lease, {@code null} when nobody holds it.
     */
    Optional<RawNonBlockingOperation> find(UUID idempotencyKey);

    /**
     * Claims anew a finished record that outlived its TTL - the row held under {@code operation.lockToken()} whose
     * status is one of {@code onStatuses}: takes its status, first-attempt flag and fingerprint from
     * {@code operation} and clears what the finished record left behind - its result, failure class, response and
     * expiry. The lease is left as it is - the same call has just taken it.
     *
     * @return the row as stored.
     * @throws OperationLockLostException if no row under this key is held under the token in one of
     *         {@code onStatuses}.
     */
    RawNonBlockingOperation updateByLockToken(RawNonBlockingOperation operation, Set<OperationStatus> onStatuses);

    /**
     * Completes the row held under {@code lockToken} whose status is {@code onStatus}: stores the result and the
     * new status, and an expiry {@code ttlMillis} from the moment of writing, by the store's clock.
     *
     * @param idempotencyKey the key of the row.
     * @param result the serialized result; {@code null} for an operation that returned none.
     * @param newStatus the status the row moves to.
     * @param ttlMillis how long the record lives from now.
     * @param lockToken the token the caller holds the claim under.
     * @param onStatus the status the row has to be in.
     * @throws OperationLockLostException if no row under this key is held under the token in {@code onStatus}.
     */
    void saveResultAndUpdateStatusByLockToken(UUID idempotencyKey,
                                              String result,
                                              OperationStatus newStatus,
                                              long ttlMillis,
                                              UUID lockToken,
                                              OperationStatus onStatus);

    /**
     * Keeps a failure on the row held under {@code lockToken} whose status is {@code onStatus}: stores the
     * serialized failure as the result, the class it is rebuilt from, the new status, and an expiry
     * {@code ttlMillis} from the moment of writing, by the store's clock.
     *
     * @param idempotencyKey the key of the row.
     * @param exception the serialized failure.
     * @param type the class of the failure.
     * @param newStatus the status the row moves to.
     * @param ttlMillis how long the record lives from now.
     * @param lockToken the token the caller holds the claim under.
     * @param onStatus the status the row has to be in.
     * @throws OperationLockLostException if no row under this key is held under the token in {@code onStatus}.
     */
    void saveExceptionAndUpdateStatusByLockToken(UUID idempotencyKey,
                                                 String exception,
                                                 ResultType type,
                                                 OperationStatus newStatus,
                                                 long ttlMillis,
                                                 UUID lockToken,
                                                 OperationStatus onStatus);

    /**
     * Removes the row held under {@code lockToken}, which frees the key altogether.
     *
     * @throws OperationLockLostException if no row under this key is held under the token.
     */
    void deleteByLockToken(UUID idempotencyKey, UUID lockToken);

    /**
     * Clears the claim on the row held under {@code lockToken}, leaving the record as it is. A row no longer held
     * under the token is left alone without a failure: the claim is already gone, which is what was asked for.
     */
    void unlock(UUID idempotencyKey, UUID lockToken);
}
