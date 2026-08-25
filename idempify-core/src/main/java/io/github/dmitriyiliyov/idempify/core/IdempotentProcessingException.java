package io.github.dmitriyiliyov.idempify.core;

public class IdempotentProcessingException extends RuntimeException {

    public IdempotentProcessingException(String message) {
        super(message);
    }

    public IdempotentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
