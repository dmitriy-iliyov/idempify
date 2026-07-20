package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Defines the contract for storing and retrieving idempotent operations.
 * This repository provides an abstraction over the underlying storage mechanism for operations.
 */
public interface OperationRepository {
    /**
     * Saves the given operation if an operation with the same idempotency key is not already present.
     *
     * @param operation the operation to save.
     * @return the saved operation, or the existing operation if one was already present.
     */
    Operation saveIfAbsent(Operation operation);


    /**
     * Updates an existing operation, transitioning it to a new state.
     * The update is performed conditionally based on the operation's current state.
     *
     * @param operation the operation to update.
     * @param onState   the expected current state of the operation for the update to proceed.
     * @return the updated operation.
     */
    Operation update(Operation operation, OperationState onState);

    /**
     * Finds an operation by its idempotency key.
     *
     * @param idempotencyKey the idempotency key of the operation.
     * @return an {@link Optional} containing the operation if found, or an empty {@link Optional} otherwise.
     */
    Optional<Operation> findByIdempotencyKey(UUID idempotencyKey);


    /**
     * Saves the result of an operation and updates its state.
     * The update is performed conditionally based on the operation's current state.
     *
     * @param result         the serialized result to save.
     * @param state          the new state of the operation.
     * @param idempotencyKey the idempotency key of the operation to update.
     * @param onState        the expected current state of the operation for the update to proceed.
     */
    void saveResultAndUpdateState(String result, OperationState state, UUID idempotencyKey, OperationState onState);
}
