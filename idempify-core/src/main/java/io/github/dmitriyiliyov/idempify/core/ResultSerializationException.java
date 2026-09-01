package io.github.dmitriyiliyov.idempify.core;

public class ResultSerializationException extends ResultProcessingException {
    public ResultSerializationException(String message, Exception e) {
        super(message, e);
    }

    public ResultSerializationException(String message) {
        super(message);
    }
}
