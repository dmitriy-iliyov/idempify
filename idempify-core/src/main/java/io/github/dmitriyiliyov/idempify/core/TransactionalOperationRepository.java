package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Write side of a store whose own atomicity decides the races, which is what a transactional processor
 * relies on instead of taking a lock.
 * <p>
 * Both guarantees are load-bearing rather than stylistic: {@link #saveIfAbsent} must let exactly one
 * concurrent request claim a key, and both updates must be conditional on the row's current status, because
 * requests for the same key run through them at the same time.
 * <p>
 * Like the read side, this interface deals in {@link RawOperation} only - an implementation stores the
 * components it is given and returns the components it holds, and never learns what they decode into.
 */
public interface TransactionalOperationRepository {

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
     * This method takes no claim of its own - the caller is required to hold the row already, in the
     * transactional path through the {@link #saveIfAbsent} that preceded it. Called without that, the
     * condition below turns into a race the caller cannot see.
     * <p>
     * A missed condition is therefore a failure rather than an outcome to inspect - the row is expected to
     * be there and to be in {@code onStatus}. An implementation must not fall back to returning what it
     * found - a row written by somebody else is indistinguishable from the caller's own once returned, and
     * everything downstream reads it as the caller's.
     * <p>
     * Every column is written from {@code operation}, {@code expires_at} included - an implementation that
     * leaves one behind would let a row reclaimed after expiry keep the expiry of the operation it replaced.
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
     * @param expiresAt      when the stored result stops being replayable.
     * @param onStatus       the expected current status of the row for the update to proceed.
     * @throws OperationStatusMismatchException if no row with this key is in {@code onStatus}.
     */
    void saveResultAndUpdateStatus(UUID idempotencyKey,
                                   String result,
                                   OperationStatus status,
                                   Instant expiresAt,
                                   OperationStatus onStatus);
}
