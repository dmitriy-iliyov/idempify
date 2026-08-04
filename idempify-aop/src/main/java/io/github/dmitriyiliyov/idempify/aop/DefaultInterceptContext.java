package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.ExternalOperationCallback;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

import java.util.Objects;
import java.util.UUID;

public class DefaultInterceptContext<T> implements InterceptContext<T> {

    private final Class<T> operationResultType;
    private final ExternalOperationCallback<T> operationCallback;
    private final OperationMetadata operationMetadata;
    private final UUID idempotencyKey;
    private final RequestContext requestContext;

    public DefaultInterceptContext(Class<T> operationResultType,
                                   ExternalOperationCallback<T> operationCallback,
                                   UUID idempotencyKey,
                                   RequestContext requestContext,
                                   OperationMetadata operationMetadata) {
        this.operationResultType = Objects.requireNonNull(operationResultType, "operationResultType cannot be null");
        this.operationCallback = Objects.requireNonNull(operationCallback, "operationCallback cannot be null");
        this.idempotencyKey = idempotencyKey;
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext cannot be null");
        this.operationMetadata = Objects.requireNonNull(operationMetadata, "operationMetadata cannot be null");
    }

    @Override
    public Class<T> getOperationResultType() {
        return operationResultType;
    }

    @Override
    public ExternalOperationCallback<T> getOperationCallback() {
        return operationCallback;
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public RequestContext getRequestContext() {
        return requestContext;
    }

    @Override
    public OperationMetadata getOperationMetadata() {
        return operationMetadata;
    }
}
