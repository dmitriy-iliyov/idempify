package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Write side of a store whose own atomicity decides the races, which is what a transactional processor
 * relies on instead of taking a lock.
 * <p>
 * Both guarantees are load-bearing rather than stylistic: {@link #saveIfAbsent} must let exactly one
 * concurrent request claim a key, and both updates must be conditional on the row's current status, because
 * requests for the same key run through them at the same time.
 */
public interface TransactionalOperationRepository extends OperationRepository {
    /**
     * Saves the given operation if an operation with the same idempotency key is not already present.
     * <p>
     * The claim must hold until the caller's transaction ends, not until this statement returns: everything
     * above rests on a second request for the same key waiting here instead of walking past a claim that has
     * already been released. An implementation whose lock lives for the statement alone lets a duplicate run
     * the business method a second time.
     *
     * @param operation the operation to save.
     * @return the saved operation, or the existing operation if one was already present.
     */
    Operation saveIfAbsent(Operation operation);

    /**
     * Updates an existing operation, transitioning it to a new status. The update is performed conditionally
     * based on the operation's current status.
     * <p>
     * This method takes no claim of its own - the caller is required to hold the row already, in the
     * transactional path through the {@link #saveIfAbsent} that preceded it. Called without that, the
     * condition below turns into a race the caller cannot see.
     * <p>
     * A missed condition is therefore a failure rather than an outcome to inspect - the row is expected to
     * be there and to be in {@code onStatus}. An implementation must not fall back to returning what it found - a row written by somebody else is indistinguishable
     * from the caller's own once returned, and everything downstream reads it as the caller's.
     *
     * @param operation the operation to update.
     * @param onStatus  the expected current status of the operation for the update to proceed.
     * @return the updated operation, never {@code null}.
     * @throws OperationStatusMismatchException if no row with this key is in {@code onStatus}.
     */
    Operation update(Operation operation, OperationStatus onStatus);

    /**
     * Saves the result of an operation and updates its status.
     * The update is performed conditionally based on the operation's current status.
     * <p>
     * As in {@link #update}, a missed condition is not an outcome to inspect but a failure: this method
     * completes an operation the caller itself started and still holds, so the row is expected to be there
     * and to be in {@code onStatus}. An implementation must not silently leave the row alone and return
     * whatever it found.
     *
     * @param result         the serialized result to save.
     * @param status         the new status of the operation.
     * @param idempotencyKey the idempotency key of the operation to update.
     * @param onStatus       the expected current status of the operation for the update to proceed.
     * @return the updated operation, never {@code null}.
     * @throws OperationStatusMismatchException if no row with this key is in {@code onStatus}.
     */
    Operation saveResultAndUpdateStatus(String result, OperationStatus status, UUID idempotencyKey, OperationStatus onStatus);
}
