package io.github.dmitriyiliyov.idempify.core;

public class LockBasedIdempotentProcessor implements IdempotentProcessor {

    @Override
    public <T> T process(OperationContext<T> context, OperationMetadata metadata) {
        return null;
    }
}
