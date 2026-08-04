package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Builds the {@link OperationMetadata} of an intercepted call from its {@link Idempotent} annotation and the
 * join point the annotation was found on.
 * <p>
 * The annotation alone does not decide the settings - it only says what this call site specifies, leaving the
 * rest to the named config it points at and to the global properties. The factory hands that partial picture
 * to the core so it can be layered into a complete one.
 */
public interface OperationMetadataFactory {

    /**
     * Returns the metadata for the intercepted method.
     * <p>
     * The result depends on the method alone, not on the call, so implementations are free to resolve it once
     * per method and reuse it. Callers may rely on that: this is invoked on every intercepted call.
     *
     * @param annotation the {@link Idempotent} the pointcut matched on.
     * @param jp         the intercepted call, used to identify the method the annotation was found on.
     */
    OperationMetadata generate(Idempotent annotation, ProceedingJoinPoint jp);
}
