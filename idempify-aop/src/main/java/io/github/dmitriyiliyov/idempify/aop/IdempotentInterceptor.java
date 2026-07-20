package io.github.dmitriyiliyov.idempify.aop;

/**
 * Defines the contract for intercepting methods annotated with {@link Idempotent}.
 * Implementations of this interface can be used to add custom logic to the idempotency check.
 */
public interface IdempotentInterceptor {
    /**
     * Intercepts the execution of an idempotent method.
     *
     * @param context the context of the interception, containing information about the method call.
     */
    <T> T intercept(InterceptContext<T> context);
}
