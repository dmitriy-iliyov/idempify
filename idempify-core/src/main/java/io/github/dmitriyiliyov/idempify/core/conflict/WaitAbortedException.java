package io.github.dmitriyiliyov.idempify.core.conflict;

public class WaitAbortedException extends RuntimeException {
    public WaitAbortedException(String message) {
        super(message);
    }
}
