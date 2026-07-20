package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.Optional;
import java.util.UUID;

/**
 * Defines the contract for handling concurrent conflicts in idempotent operations.
 * A conflict occurs when an operation with the same idempotency key is already in progress.
 */
public interface ConflictHandler {
    /**
     * Handles a conflict for the given idempotency key.
     *
     * @param idempotencyKey the idempotency key that caused the conflict.
     * @param c              the expected type of the response.
     * @param <T>            the type of the response.
     * @return an optional containing the response from the original operation, or an empty optional if the response is not available.
     */
    <T> Optional<T> handle(UUID idempotencyKey, Class<T> c);

    /**
     * Returns the strategy that this handler implements.
     */
    ConflictHandleStrategy getStrategy();
}
