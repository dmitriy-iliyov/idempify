package io.github.dmitriyiliyov.idempify.core;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException(Exception e) {
        super("Idempotency key has invalid format", e);
    }
}
