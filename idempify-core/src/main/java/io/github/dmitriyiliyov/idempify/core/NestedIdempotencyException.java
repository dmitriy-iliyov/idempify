package io.github.dmitriyiliyov.idempify.core;

public class NestedIdempotencyException extends MisplacedException {
    public NestedIdempotencyException() {
        super("@Idempotent operation cannot run inside another one: @Idempotent belongs on an entry point");
    }
}
