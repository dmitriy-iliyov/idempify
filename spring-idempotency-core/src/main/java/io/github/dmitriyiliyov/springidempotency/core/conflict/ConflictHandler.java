package io.github.dmitriyiliyov.springidempotency.core.conflict;

import java.util.Optional;
import java.util.UUID;

public interface ConflictHandler {
    <T> Optional<T> handle(UUID idempotencyKey, Class<T> c);
    ConflictHandleStrategy getStrategy();
}
