package io.github.dmitriyiliyov.idempify.aop;

/**
 * The step between the aspect and the core: takes what the aspect observed about an intercepted call and
 * returns the value the caller should receive, whether that came from running the method or from replaying an
 * earlier result.
 */
public interface IdempotentInterceptor {

    /**
     * Processes the intercepted call.
     *
     * @param context what the aspect observed about the call.
     * @return the value to return to the caller.
     * @throws Throwable what the intercepted method threw, exactly as it was thrown.
     */
    Object intercept(InterceptContext context) throws Throwable;
}
