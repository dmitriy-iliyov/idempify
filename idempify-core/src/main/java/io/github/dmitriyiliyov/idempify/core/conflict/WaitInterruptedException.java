package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.UUID;

public class WaitInterruptedException extends WaitAbortedException {
    public WaitInterruptedException(UUID idempotencyKey, InterruptedException cause) {
        super("Operation (idempotencyKey=%s) was interrupted while waiting for it to complete".formatted(idempotencyKey), cause);
    }
}
