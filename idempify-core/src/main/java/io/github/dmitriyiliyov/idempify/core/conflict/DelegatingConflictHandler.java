package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;

import java.util.Optional;

/**
 * Defines the contract for a composite conflict handler that delegates to other conflict handlers based on the operation metadata.
 */
public interface DelegatingConflictHandler {
    /**
     * Handles a conflict for the given operation metadata.
     *
     * @param metadata the metadata of the operation that caused the conflict.
     * @param c        the expected type of the response.
     * @param <T>      the type of the response.
     * @return an optional containing the response from the original operation, or an empty optional if the response is not available.
     */
    <T> Optional<T> handle(OperationMetadata metadata, Class<T> c);
}
