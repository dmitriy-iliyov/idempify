package io.github.dmitriyiliyov.idempify.core;

public class EmptyIdempotencyKeyException extends RuntimeException {
    public EmptyIdempotencyKeyException(String message) {
        super(message);
    }
}
