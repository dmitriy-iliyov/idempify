package io.github.dmitriyiliyov.idempify.core;

/**
 * Defines the possible states of an idempotent operation.
 */
public enum OperationState {
    /**
     * The operation is currently in process.
     */
    IN_PROCESS,

    /**
     * A conflict has been detected. This means that another operation with the same idempotency key is already in progress.
     */
    CONFLICT,

    /**
     * The operation has been successfully processed.
     */
    PROCESSED
}
