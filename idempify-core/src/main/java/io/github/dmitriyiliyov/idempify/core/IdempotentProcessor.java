package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

/**
 * Runs one intercepted call under its idempotency guarantee: claims the key, and from there either runs the
 * caller's business operation exactly once or replays what an earlier call under the same key produced.
 * <p>
 * Implementations differ in where the operation's record lives relative to the business transaction, which is
 * what {@link ProcessorType} names.
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
     */
    Object process(OperationContext context, OperationMetadata metadata);
}
