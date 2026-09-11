package io.github.dmitriyiliyov.idempify.core;

/**
 * A value could not be turned into the text the record keeps, or read back out of it - in either direction and
 * whether the value was the result of the operation or its response.
 * <p>
 * The transport catches this group rather than its members: both directions leave the caller with the same
 * thing, an operation whose stored answer cannot be reproduced.
 */
public class SerializationProcessingException extends RuntimeException {
    public SerializationProcessingException(String message) {
        super(message);
    }

    public SerializationProcessingException(String message, Exception e) {
        super(message, e);
    }
}
