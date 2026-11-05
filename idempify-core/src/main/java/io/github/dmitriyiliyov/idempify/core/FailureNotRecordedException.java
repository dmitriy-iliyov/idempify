package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Thrown when the business method failed and its failure could neither release the key nor be kept on the
 * record. The cause is the failure of the store; what the business method threw travels as a suppressed
 * exception, so neither is lost.
 */
public class FailureNotRecordedException extends OutcomeNotRecordedException {
    public FailureNotRecordedException(UUID idempotencyKey, Throwable cause, Throwable businessFailure) {
        super("Operation (idempotencyKey=%s) could not be released or recorded after it failed".formatted(idempotencyKey), cause);
        if (businessFailure != null && businessFailure != cause) {
            addSuppressed(businessFailure);
        }
    }
}
