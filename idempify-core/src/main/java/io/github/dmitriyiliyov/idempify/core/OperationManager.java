package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;

/**
 * Manages the lifecycle of an idempotent operation.
 */
public interface OperationManager {
    /**
     * Starts a new idempotent operation or returns the result of a previously completed operation.
     *
     * @param metadata the metadata of the operation.
     * @param c        the expected type of the response.
     * @param <T>      the type of the response.
     * @return an optional containing the response from the operation, or an empty optional if the operation is new.
     */
    <T> Optional<T> startOrReply(OperationMetadata metadata, Class<T> c);

    /**
     * Completes an idempotent operation and stores its result.
     *
     * @param metadata the metadata of the operation.
     * @param response the response of the operation.
     * @param <T>      the type of the response.
     * @return the response of the operation.
     */
    <T> T complete(OperationMetadata metadata, T response);
}
