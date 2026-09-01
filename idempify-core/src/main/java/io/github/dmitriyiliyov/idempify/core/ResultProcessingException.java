package io.github.dmitriyiliyov.idempify.core;

public class ResultProcessingException extends RuntimeException {
    public ResultProcessingException(String message) {
        super(message);
    }

    public ResultProcessingException(String message, Exception e) {
        super(message, e);
    }
}
