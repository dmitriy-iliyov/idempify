package io.github.dmitriyiliyov.springidempotency.aop;

public interface IdempotentInterceptor {
    void intercept(InterceptContext context);
}
