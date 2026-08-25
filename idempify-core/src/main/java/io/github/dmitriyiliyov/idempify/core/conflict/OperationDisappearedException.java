package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.UUID;

public class OperationDisappearedException extends RuntimeException {
    public OperationDisappearedException(UUID idempotencyKey) {
        super("Operation (idempotencyKey=%s) disappeared while waiting for it to complete".formatted(idempotencyKey));
    }
}
