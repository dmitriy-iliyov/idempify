package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.Optional;
import java.util.UUID;

public class RejectConflictHandler implements ConflictHandler {

    @Override
    public <T> Optional<T> handle(UUID idempotencyKey, Class<T> c) {
        throw new IdempotencyConflictException("Operation in process by another request");
    }

    @Override
    public ConflictHandleStrategy getStrategy() {
        return ConflictHandleStrategy.REJECT;
    }
}
