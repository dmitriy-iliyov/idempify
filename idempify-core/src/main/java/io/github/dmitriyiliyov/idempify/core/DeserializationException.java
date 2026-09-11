package io.github.dmitriyiliyov.idempify.core;

public class DeserializationException extends SerializationProcessingException {
    public DeserializationException(String message) {
        super(message);
    }

    public DeserializationException(String message, Exception e) {
        super(message, e);
    }
}
