package io.github.dmitriyiliyov.springidempotency.aop;

import io.github.dmitriyiliyov.springidempotency.core.RequestContext;

import java.util.UUID;
import java.util.function.Supplier;

public interface InterceptContext {
    Idempotent getAnnotation();

    UUID getIdempotencyKey();

    RequestContext getRequestContext();

    Supplier<Object> getDelegate();
}
