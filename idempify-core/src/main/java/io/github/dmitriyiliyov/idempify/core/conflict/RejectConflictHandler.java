package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.NonBlockingOperation;

public class RejectConflictHandler implements ConflictHandler {

    @Override
    public NonBlockingOperation handle(ConflictContext context) {
        throw new IdempotencyConflictException(
                "Operation (idempotencyKey=%s) is already in progress for another request"
                        .formatted(context.getIdempotencyKey())
        );
    }
}
