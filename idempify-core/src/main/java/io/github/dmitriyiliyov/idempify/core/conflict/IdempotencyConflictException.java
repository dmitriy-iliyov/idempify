package io.github.dmitriyiliyov.idempify.core.conflict;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
