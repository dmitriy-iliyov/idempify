package io.github.dmitriyiliyov.idempify.core;

@FunctionalInterface
public interface IdempotentOperation<T> {
    T call() throws Throwable;
}
