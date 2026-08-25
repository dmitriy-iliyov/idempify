package io.github.dmitriyiliyov.idempify.core;

/**
 * Runs one intercepted call under its idempotency guarantee: claims the key, and from there either runs the
 * caller's business operation exactly once or replays what an earlier call under the same key produced.
 * <p>
 * Implementations differ in where the operation's record lives relative to the business transaction, which is
 * what {@link ProcessorType} names.
 */
public interface IdempotentProcessor {

    /**
     * Returns the value the caller should receive, having run the business operation only if this call turned
     * out to be the first attempt for its key.
     *
     * @param context  what identifies this call and how to run it.
     * @param metadata the settings resolved for its call site.
     * @param <T>      the type of the result.
     */
    <T> T process(OperationContext<T> context, OperationMetadata metadata);
}
