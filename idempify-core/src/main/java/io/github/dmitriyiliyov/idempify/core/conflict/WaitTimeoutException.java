package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.UUID;

public class WaitTimeoutException extends WaitAbortedException {
    public WaitTimeoutException(UUID idempotencyKey) {
        super("Operation (idempotencyKey=%s) did not complete within the configured max duration".formatted(idempotencyKey));
    }
}
