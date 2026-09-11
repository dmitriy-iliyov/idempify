package io.github.dmitriyiliyov.idempify.core.conflict;

public class RejectConflictHandler implements ConflictHandler {

    @Override
    public Object handle(ConflictContext context) {
        throw new IdempotencyConflictException(
                "Operation (idempotencyKey=%s) is already in progress for another request"
                        .formatted(context.getIdempotencyKey())
        );
    }

    @Override
    public boolean requiresTransaction() {
        return true;
    }
}
