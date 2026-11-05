package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Thrown when the key could not be claimed: the business method has not run, so the call left no effect and a
 * repeat is safe.
 */
public class OperationLockFailedException extends EarlyFailureException {
    public OperationLockFailedException(UUID idempotencyKey, Throwable cause) {
        super("Operation (idempotencyKey=%s) could not be claimed".formatted(idempotencyKey), cause);
    }
}
