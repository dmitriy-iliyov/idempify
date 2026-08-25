package io.github.dmitriyiliyov.idempify.core;

import java.lang.reflect.Method;

/**
 * Resolves the {@link OperationMetadata} of an {@link Idempotent} method: reads the annotation off the method,
 * hands what it specifies to the layering of configuration sources, and remembers the answer.
 * <p>
 * The single way to get metadata for a method, and deliberately so - the aspect that intercepts the call and a
 * transport module that scans endpoints at startup both come through here, so both see the same instance for
 * the same method and cannot drift apart.
 * <p>
 * The annotation is read from the method rather than passed in: the key an implementation caches by is the
 * method it normalized, and taking the annotation from anywhere else would allow the two to disagree.
 *
 * @see OperationMetadataCache
 * @see OperationMetadataManager
 */
public interface OperationMetadataResolver {

    /**
     * Returns the metadata of the annotated method.
     * <p>
     * The result depends on the method alone, not on the call, so implementations resolve it once and reuse
     * it. Callers may rely on that: this is invoked on every intercepted call.
     *
     * @param method      the annotated method, as reported by a join point or a handler mapping.
     * @param targetClass the class the call is dispatched on, used to find the implementation of an interface
     *                    or bridge method; may be {@code null} when there is no target.
     * @throws IllegalStateException if the method carries no {@link Idempotent}.
     */
    OperationMetadata resolve(Method method, Class<?> targetClass);
}
