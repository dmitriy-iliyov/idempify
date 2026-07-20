package io.github.dmitriyiliyov.idempify.core;

/**
 * Defines the contract for processing an idempotent operation.
 */
public interface IdempotentProcessor {
    /**
     * Processes an idempotent operation.
     *
     * @param metadata   the metadata of the operation.
     * @param resultType the type of the result.
     * @param call       a supplier that provides the result of the operation.
     * @param <T>        the type of the result.
     * @return the result of the operation.
     */
    <T> T process(OperationMetadata metadata, Class<T> resultType, IdempotentOperation<T> call);
}
