package io.github.dmitriyiliyov.idempify.core;

public class SerializationException extends SerializationProcessingException {
    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Exception e) {
        super(message, e);
    }
}
