package io.github.dmitriyiliyov.idempify.core.conflict;

public class OperationInProgressException extends ConflictException {
    public OperationInProgressException(String message) {
        super(message);
    }
}
