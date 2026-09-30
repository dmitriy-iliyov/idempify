package io.github.dmitriyiliyov.idempify.core;

import java.util.Objects;

public abstract class AbstractIdempotentProcessorDecorator implements IdempotentProcessor {

    protected final IdempotentProcessor delegate;

    public AbstractIdempotentProcessorDecorator(IdempotentProcessor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public Object process(OperationContext context, OperationMetadata metadata) throws Throwable {
        return delegate.process(context, metadata);
    }
}
