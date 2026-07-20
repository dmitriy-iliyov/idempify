package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentOperation;
import io.github.dmitriyiliyov.idempify.core.RequestContext;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Provides context for an idempotent method interception.
 * This interface gives access to the idempotency key, the request context, and the original method call.
 * @param <T> the type of the method return value
 */
public interface InterceptContext<T> {

    /**
     * Returns the result type of the intercepted method (operation).
     */
    Class<T> getOperationResultType();

    /**
     * Returns a supplier that can be used to invoke the original method (operation).
     */
    IdempotentOperation<T> getOperation();

    /**
     * Returns the context of the current request.
     */
    RequestContext getRequestContext();

    /**
     * Returns the idempotency key for the current request.
     */
    UUID getIdempotencyKey();

    /**
     * Returns the name of the header or metadata property that contains the idempotency key.
     */
    String getHeaderName();

    /**
     * Returns the TTL for the idempotency key.
     */
    long getTtl();

    /**
     * Returns the unit of time for the TTL.
     */
    TimeUnit getTimeUnit();

    /**
     * Returns the strategy to apply when a conflict is detected.
     */
    ConflictHandleStrategy getConflictHandleStrategy();

    /**
     * Returns the custom conflict handler class to use.
     */
    Class<? extends ConflictHandler> getConflictHandlerClass();

    /**
     * Returns whether to use a fingerprint to identify the request payload.
     */
    boolean useFingerprint();

    /**
     * Returns the policy for generating a fingerprint from the request.
     */
    Class<? extends FingerprintPolicy> getFingerprintPolicyClass();
}
