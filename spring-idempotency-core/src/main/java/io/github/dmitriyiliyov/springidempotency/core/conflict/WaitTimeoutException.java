package io.github.dmitriyiliyov.springidempotency.core.conflict;

public class WaitTimeoutException extends RuntimeException {
    public WaitTimeoutException(String message) {
        super(message);
    }
}
