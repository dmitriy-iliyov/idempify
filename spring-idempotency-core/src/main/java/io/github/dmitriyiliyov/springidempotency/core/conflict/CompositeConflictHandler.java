package io.github.dmitriyiliyov.springidempotency.core.conflict;

import io.github.dmitriyiliyov.springidempotency.core.OperationMetadata;

import java.util.Optional;

public interface CompositeConflictHandler {
    <T> Optional<T> handle(OperationMetadata metadata, Class<T> c);
}
