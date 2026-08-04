package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;

import java.lang.reflect.Method;

/**
 * Remembers the {@link OperationMetadata} already resolved for an intercepted method, so that the layering of
 * configuration sources runs once per method rather than once per call.
 * <p>
 * Implementations must be safe for concurrent use: several requests may reach the same method at once.
 */
public interface OperationMetadataCache {

    /**
     * Returns the metadata resolved for the method, or {@code null} if it has not been resolved yet.
     *
     * @param method the intercepted method, as reported by the join point's signature.
     */
    OperationMetadata get(Method method);

    /**
     * Stores the metadata resolved for the method, replacing whatever was stored for it before.
     *
     * @param method   the intercepted method, as reported by the join point's signature.
     * @param metadata the metadata to remember for it.
     */
    void put(Method method, OperationMetadata metadata);
}
