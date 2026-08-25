package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.UUID;

public class WaitAttemptsExhaustedException extends WaitAbortedException {
    public WaitAttemptsExhaustedException(UUID idempotencyKey) {
        super("Operation (idempotencyKey=%s) did not complete within the configured number of attempts".formatted(idempotencyKey));
    }
}
