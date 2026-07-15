package io.github.dmitriyiliyov.springidempotency.core;

import java.util.function.Supplier;

public interface IdempotentProcessor {
    <T> T process(OperationMetadata metadata, Supplier<T> supplier);
}
