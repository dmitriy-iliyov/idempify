package io.github.dmitriyiliyov.idempify.core;

public interface OperationResponseCachePolicy {
    boolean shouldCache5xx();
}
