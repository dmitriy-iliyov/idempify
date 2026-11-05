package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Thrown when the library failed before the business method ran - claiming the key, reading or decoding its
 * record, opening the transaction, committing a replay - so the call left no effect and a repeat is safe. The
 * cause is the failure itself.
 */
public class EarlyFailureException extends IdempotentProcessingException {

    public EarlyFailureException(UUID idempotencyKey, Throwable cause) {
        this("Operation (idempotencyKey=%s) failed before the business method ran".formatted(idempotencyKey), cause);
    }

    public EarlyFailureException(String message) {
        super(message);
    }

    public EarlyFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
