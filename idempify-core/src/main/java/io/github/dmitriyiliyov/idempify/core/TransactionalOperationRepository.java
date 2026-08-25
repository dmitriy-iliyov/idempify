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
     *
     * @param operation the operation to save.
     * @return the saved operation, or the existing operation if one was already present.
     */
    Operation saveIfAbsent(Operation operation);

    /**
     * Updates an existing operation, transitioning it to a new status.
     * The update is performed conditionally based on the operation's current status.
     *
     * @param operation the operation to update.
     * @param onStatus  the expected current status of the operation for the update to proceed.
     * @return the updated operation.
     */
    Operation update(Operation operation, OperationStatus onStatus);

    /**
     * Saves the result of an operation and updates its status.
     * The update is performed conditionally based on the operation's current status.
     * <p>
     * Unlike {@link #update}, a missed condition is not an outcome to inspect but a failure: this method
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
