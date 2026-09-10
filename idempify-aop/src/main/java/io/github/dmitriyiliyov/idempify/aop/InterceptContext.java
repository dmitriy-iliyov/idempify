package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.ExternalOperationCallback;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.util.UUID;

/**
 * Everything the aspect observed about one intercepted call, handed to the {@link IdempotentInterceptor}.
 * <p>
 * It carries both what identifies the call - the key, the request it arrived in, the method to run - and the
 * settings resolved for its call site. The key is the one place where "not known yet" is allowed: the aspect
 * only fills it in when the annotation names it, leaving the interceptor to extract it from the request
 * otherwise.
 */
public interface InterceptContext {

    /**
     * Returns the declared return type of the intercepted method, which a replayed result is deserialized
     * back into.
     */
    ResultType getOperationResultType();

    /**
     * Returns the caller's business operation to run when this is the first attempt.
     */
    ExternalOperationCallback getOperationCallback();

    /**
     * Returns the key named by {@code @Idempotent(idempotencyKey = ...)}, or {@code null} when the annotation
     * did not name one or its expression could not be resolved - in which case the key is to be extracted
     * from the request instead.
     */
    UUID getIdempotencyKey();

    /**
     * Returns the context of the request the call arrived in, the source of both the idempotency key and the
     * fingerprint.
     */
    RequestContext getRequestContext();

    /**
     * Returns the settings resolved for this call site.
     */
    OperationMetadata getOperationMetadata();
}
