package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Thrown when a conditional write finds no row in the status it required - the row is gone, or someone else
 * has already moved it on.
 * <p>
 * This is not the ordinary outcome of two requests racing for one key: a duplicate never reaches the write
 * that completes an operation, it is answered from the record the winner left behind. Seeing this means the
 * record was changed by something outside the flow - an external cleanup of expired rows, a manual edit, or a
 * duplicate that was let through and ran the business logic a second time.
 */
public class OperationStatusMismatchException extends RuntimeException {

    public OperationStatusMismatchException(UUID idempotencyKey, OperationStatus expectedStatus) {
        super("Operation (idempotencyKey=%s) was not in expected status %s".formatted(idempotencyKey, expectedStatus));
    }
}
