package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentOperation;
import io.github.dmitriyiliyov.idempify.core.RequestContext;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DefaultInterceptContext<T> implements InterceptContext<T> {

    private final Class<T> resultType;
    private final IdempotentOperation<Object> operation;
    private final RequestContext requestContext;
    private final UUID idempotentKey;
    private final String headerName;
    private final long ttl;
    private final TimeUnit timeUnit;
    private final ConflictHandleStrategy conflictHandleStrategy;
    private final Class<? extends ConflictHandler> conflictHandlerClass;
    private final boolean useFingerprint;
    private final Class<? extends FingerprintPolicy> fingerprintPolicyClass;

    public DefaultInterceptContext(Class<T> resultType,
                                   IdempotentOperation<Object> operation,
                                   RequestContext requestContext,
                                   UUID idempotentKey,
                                   String headerName,
                                   Long ttl,
                                   TimeUnit timeUnit,
                                   ConflictHandleStrategy conflictHandleStrategy,
                                   Class<? extends ConflictHandler> conflictHandlerClass,
                                   boolean useFingerprint,
                                   Class<? extends FingerprintPolicy> fingerprintPolicyClass) {
        this.resultType = Objects.requireNonNull(resultType, "resultType cannot be null");
        this.operation = Objects.requireNonNull(operation, "methodCall cannot be null");
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext cannot be null");
        this.idempotentKey = idempotentKey;
        this.headerName = Objects.requireNonNull(headerName, "headerName cannot be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl cannot be null");
        this.timeUnit = Objects.requireNonNull(timeUnit, "timeUnit cannot be null");
        this.conflictHandleStrategy = Objects.requireNonNull(conflictHandleStrategy, "conflictHandleStrategy cannot be null");
        this.conflictHandlerClass = Objects.requireNonNull(conflictHandlerClass, "conflictHandlerClass cannot be null");
        this.useFingerprint = useFingerprint;
        this.fingerprintPolicyClass = Objects.requireNonNull(fingerprintPolicyClass, "fingerprintPolicyClass cannot be null");
    }

    @Override
    public RequestContext getRequestContext() {
        return requestContext;
    }

    @Override
    public Class<T> getOperationResultType() {
        return resultType;
    }

    @SuppressWarnings("unchecked")
    public IdempotentOperation<T> getOperation() {
        return () -> (T) operation.call();
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotentKey;
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public long getTtl() {
        return ttl;
    }

    @Override
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    @Override
    public ConflictHandleStrategy getConflictHandleStrategy() {
        return conflictHandleStrategy;
    }

    @Override
    public Class<? extends ConflictHandler> getConflictHandlerClass() {
        return conflictHandlerClass;
    }

    @Override
    public boolean useFingerprint() {
        return useFingerprint;
    }

    @Override
    public Class<? extends FingerprintPolicy> getFingerprintPolicyClass() {
        return fingerprintPolicyClass;
    }
}
