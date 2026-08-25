package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.UUID;

public class RejectConflictHandler implements ConflictHandler {

    @Override
    public <T> T handle(ConflictContext<T> context) {
        UUID idempotencyKey = context.getIdempotencyKey();
        throw new IdempotencyConflictException(
                "Operation (idempotencyKey=%s) is already in progress for another request".formatted(idempotencyKey)
        );
    }

    @Override
    public boolean requiresTransaction() {
        return true;
    }
}
