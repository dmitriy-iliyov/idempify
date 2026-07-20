package io.github.dmitriyiliyov.idempify.core.conflict;

public class WaitTimeoutException extends RuntimeException {
    public WaitTimeoutException(String message) {
        super(message);
    }
}
