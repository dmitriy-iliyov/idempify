package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Write side of a store whose own atomicity decides the races, which is what the blocking processor relies
 * on: the claim is scoped to the caller's transaction, so a duplicate waits on it rather than meeting it.
 * <p>
 * Both guarantees are load-bearing rather than stylistic: {@link #saveIfAbsent} must let exactly one
 * concurrent request claim a key, and both updates must be conditional on the row's current status, because
 * requests for the same key run through them at the same time.
 * <p>
 * This interface deals in {@link RawOperation} only, and never learns what its components decode into. Time is the store's own: it sets the creation time itself, counts an expiry from the TTL it is
 * handed, and answers every read with the time the row has left by its own clock - so no two callers ever
 * compare a stored moment against clocks of their own.
 */
public interface BlockingOperationRepository {

    /**
     * Saves the given row if a row with the same idempotency key is not already present.
     * <p>
     * The claim must hold until the caller's transaction ends, not until this statement returns: everything
     * above rests on a second request for the same key waiting here instead of walking past a claim that has
     * already been released. An implementation whose lock lives for the statement alone lets a duplicate run
     * the business method a second time.
     * <p>
     * A claim carries no expiry: {@code expires_at} is written only when the operation completes, so the
     * row this method inserts leaves it {@code NULL}.
     *
     * @param operation the row to save.
     * @return the saved row, or the existing row if one was already present.
     */
    RawOperation saveIfAbsent(RawOperation operation);

    /**
     * Updates an existing row, transitioning it to a new status. The update is performed conditionally
     * based on the row's current status.
     * <p>
     * This method takes no claim of its own - the caller is required to hold the row already, claimed by
     * the {@link #saveIfAbsent} that preceded it in the same transaction. Called without that, the
     * condition below turns into a race the caller cannot see.
     * <p>
     * A missed condition is therefore a failure rather than an outcome to inspect - the row is expected to
     * be there and to be in {@code onStatus}. An implementation must not fall back to returning what it
     * found - a row written by somebody else is indistinguishable from the caller's own once returned, and
     * everything downstream reads it as the caller's.
     * <p>
     * The columns the row carries are written from {@code operation}, and the expiry is cleared - an
     * implementation that leaves it behind would let a row reclaimed after expiry keep the expiry of the
     * operation it replaced.
     *
     * @param operation the row to update.
     * @param onStatus  the expected current status of the row for the update to proceed.
     * @return the updated row, never {@code null}.
     * @throws OperationStatusMismatchException if no row with this key is in {@code onStatus}.
     */
    RawOperation update(RawOperation operation, OperationStatus onStatus);

    /**
     * Saves the serialized result of an operation and updates its status.
     * The update is performed conditionally based on the row's current status.
     * <p>
     * As in {@link #update}, a missed condition is not an outcome to inspect but a failure: this method
     * completes an operation the caller itself started and still holds, so the row is expected to be there
     * and to be in {@code onStatus}. An implementation must not silently leave the row alone: nothing is
     * handed back for the caller to notice the miss by.
     * <p>
     * This is the only method that puts an expiry on a row. {@link #saveIfAbsent} claims a key without one,
     * so the column must be nullable and reads must survive {@code NULL}; the invariant that buys is the one
     * {@link Operation#isExpired} leans on - an expiry exists exactly on a completed operation.
     *
     * @param idempotencyKey the idempotency key of the row to update.
     * @param result         the already serialized result to save.
     * @param status         the new status of the operation.
     * @param ttlMillis      how long the stored result stays replayable, counted by the store from the moment
     *                       of this write.
     * @param onStatus       the expected current status of the row for the update to proceed.
     * @throws OperationStatusMismatchException if no row with this key is in {@code onStatus}.
     */
    void saveResultAndUpdateStatus(UUID idempotencyKey,
                                   String result,
                                   OperationStatus status,
                                   long ttlMillis,
                                   OperationStatus onStatus);
}
