package io.github.dmitriyiliyov.idempify.core;

public class ForeignTransactionException extends MisplacedException {
    public ForeignTransactionException(String message) {
        super(message);
    }
}
