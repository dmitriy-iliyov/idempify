package io.github.dmitriyiliyov.idempify.core;

public class IdempotencyKeyException extends RuntimeException {

    public IdempotencyKeyException(String message) {
        super(message);
    }

    public IdempotencyKeyException(String message, Exception e) {
        super(message, e);
    }
}
