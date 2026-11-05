package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Thrown when a way out of a non-blocking claim finds no row held under the caller's lock token: the row is
 * gone, or another call has taken the key over, or it is no longer in the status the write required.
 * <p>
 * Unlike {@link OperationStatusMismatchException}, this is an ordinary outcome of a lease rather than a sign of
 * something outside the flow: the business method outlasted the lock TTL, and the store let another call claim
 * the key. Under {@link ProcessorType#EXTERNAL_NON_BLOCKING} that call may have run the operation a second time.
 */
public class OperationLockLostException extends IdempifyException {
    public OperationLockLostException(UUID idempotencyKey) {
        super("Operation (idempotencyKey=%s) is no longer held under its lock token".formatted(idempotencyKey));
    }
}
