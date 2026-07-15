package io.github.dmitriyiliyov.springidempotency.aop;

import io.github.dmitriyiliyov.springidempotency.core.RequestContext;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class DefaultInterceptContext implements InterceptContext {

    private final Idempotent annotation;
    private final UUID idempotentKey;
    private final RequestContext requestContext;
    private final Supplier<Object> delegate;

    public DefaultInterceptContext(Idempotent annotation,
                                   UUID idempotentKey,
                                   RequestContext requestContext,
                                   Supplier<Object> delegate) {
        this.annotation = Objects.requireNonNull(annotation, "annotation cannot be null");
        this.idempotentKey = idempotentKey;
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext cannot be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public Idempotent getAnnotation() {
        return annotation;
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotentKey;
    }

    @Override
    public RequestContext getRequestContext() {
        return requestContext;
    }

    @Override
    public Supplier<Object> getDelegate() {
        return delegate;
    }
}
