package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Thrown when the business method returned but its result could not be written; the cause is the failure of the
 * store or of the commit.
 */
public class ResultNotRecordedException extends OutcomeNotRecordedException {

    public ResultNotRecordedException(UUID idempotencyKey, Throwable cause) {
        super("Operation (idempotencyKey=%s) ran, but its result could not be recorded".formatted(idempotencyKey), cause);
    }
}
