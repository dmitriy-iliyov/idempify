package io.github.dmitriyiliyov.idempify.core.conflict;

public class WaitAbortedException extends ConflictException {
    public WaitAbortedException(String message) {
        super(message);
    }

    public WaitAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
