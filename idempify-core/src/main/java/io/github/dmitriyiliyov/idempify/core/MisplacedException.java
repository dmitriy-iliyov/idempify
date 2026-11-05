package io.github.dmitriyiliyov.idempify.core;

public class MisplacedException extends IdempotentProcessingException {
    public MisplacedException(String message) {
        super(message);
    }
}
