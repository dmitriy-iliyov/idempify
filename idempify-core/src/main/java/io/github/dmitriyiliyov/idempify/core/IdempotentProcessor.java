package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

/**
 * Runs one intercepted call under its idempotency guarantee: claims the key, and from there either runs the
 * caller's business operation or replays what an earlier call under the same key produced.
 * <p>
 * Implementations differ in what {@link ProcessorType} names: whether a duplicate that arrives while the first
 * call is still running can wait for it, and whether the record of the outcome commits together with the
 * business effect - and with that, whether the operation runs at most once or at least once.
 * <p>
 * The result is an {@code Object} throughout this chain: the wrapped method's return type is known only at
 * runtime and travels beside the call as a {@link ResultType}. A caller that does know the type casts once
 * at its own entry point.
 */
public interface IdempotentProcessor {

    /**
     * Returns the value the caller should receive, having run the business operation only if this call turned
     * out to be the first attempt for its key.
     *
     * @param context  what identifies this call and how to run it.
     * @param metadata the settings resolved for its call site.
     * @throws Throwable what the business operation threw, or the failure it left on the record, exactly as it
     *         was thrown, so that the caller handles it the same way as without the library in between; a refusal
     *         or a failure of the library itself as an {@link IdempifyException}, and an {@link Error} as it is.
     */
    Object process(OperationContext context, OperationMetadata metadata) throws Throwable;
}
