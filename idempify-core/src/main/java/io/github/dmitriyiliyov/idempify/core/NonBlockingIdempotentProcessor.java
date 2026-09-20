package io.github.dmitriyiliyov.idempify.core;

public class NonBlockingIdempotentProcessor implements IdempotentProcessor {

    @Override
    public Object process(OperationContext context, OperationMetadata metadata) {
        return null;
    }
}
