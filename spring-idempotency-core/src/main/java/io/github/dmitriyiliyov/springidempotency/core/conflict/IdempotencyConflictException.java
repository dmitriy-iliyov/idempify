package io.github.dmitriyiliyov.springidempotency.core.conflict;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
