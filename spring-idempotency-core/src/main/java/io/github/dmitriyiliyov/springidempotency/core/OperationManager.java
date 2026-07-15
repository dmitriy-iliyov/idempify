package io.github.dmitriyiliyov.springidempotency.core;

import java.util.Optional;

public interface OperationManager {
    <T> Optional<T> startOrReply(OperationMetadata metadata, Class<T> c);
    <T> T complete(OperationMetadata metadata, T response);
}
