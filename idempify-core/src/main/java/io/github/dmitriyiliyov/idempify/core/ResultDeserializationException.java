package io.github.dmitriyiliyov.idempify.core;

public class ResultDeserializationException extends ResultProcessingException {
    public ResultDeserializationException(String message) {
        super(message);
    }

    public ResultDeserializationException(String message, Exception e) {
        super(message, e);
    }
}
