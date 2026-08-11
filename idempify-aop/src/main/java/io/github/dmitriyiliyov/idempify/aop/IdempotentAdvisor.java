package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.Idempotent;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationException;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

@Aspect
public class IdempotentAdvisor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAdvisor.class);
    private final IdempotentOperationExpressionEvaluator expressionEvaluator;
    private final RequestContextProvider requestContextProvider;
    private final OperationMetadataResolver metadataResolver;
    private final IdempotentInterceptor interceptor;

    public IdempotentAdvisor(IdempotentOperationExpressionEvaluator expressionEvaluator,
                             RequestContextProvider requestContextProvider,
                             OperationMetadataResolver metadataResolver,
                             IdempotentInterceptor interceptor) {
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator, "expressionEvaluator cannot be null");
        this.requestContextProvider = Objects.requireNonNull(requestContextProvider, "requestContextProvider cannot be null");
        this.metadataResolver = Objects.requireNonNull(metadataResolver, "metadataResolver cannot be null");
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor cannot be null");
    }

    @Pointcut("@annotation(idempotent) && execution(public * * (..))")
    public void pointcut(Idempotent idempotent) { }

    @Around(
            value = "pointcut(annotation)",
            argNames = "jp,annotation"
    )
    @SuppressWarnings("unchecked")
    public Object advice(ProceedingJoinPoint jp, Idempotent annotation) throws Throwable {
        UUID idempotencyKey = parseIdempotencyKey(jp, annotation);

        MethodSignature signature = (MethodSignature) jp.getSignature();
        Object target = jp.getTarget();
        OperationMetadata operationMetadata = metadataResolver.resolve(
                signature.getMethod(),
                target == null ? null : target.getClass()
        );

        RequestContext requestContext = requestContextProvider.getContext();
        return interceptor.intercept(
                buildContext(
                        signature.getReturnType(),
                        jp,
                        idempotencyKey,
                        requestContext,
                        operationMetadata
                )
        );
    }

    @SuppressWarnings("unchecked")
    private <T> InterceptContext<T> buildContext(Class<T> operationResultType,
                                                 ProceedingJoinPoint jp,
                                                 UUID idempotencyKey,
                                                 RequestContext requestContext,
                                                 OperationMetadata operationMetadata) {
        return new DefaultInterceptContext<>(
                operationResultType,
                () -> (T) jp.proceed(),
                idempotencyKey,
                requestContext,
                operationMetadata
        );
    }

    private UUID parseIdempotencyKey(JoinPoint jp, Idempotent annotation) {
        String spel = annotation.idempotencyKey();

        if (spel.isBlank()) {
            return null;
        }

        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        Object target = jp.getTarget();

        Object idempotencyKey;
        try {
            idempotencyKey = expressionEvaluator.evaluateIdempotencyKey(
                    spel,
                    method,
                    target == null ? null : target.getClass(),
                    target,
                    jp.getArgs()
            );
        } catch (EvaluationException ee) {
            log.warn("Cannot evaluate idempotency key expression '{}'", spel, ee);
            throw ee;
        }

        if (idempotencyKey == null) {
            log.warn("Idempotency key expression '{}' evaluated to null", spel);
            throw new IllegalArgumentException(
                    "Idempotency key expression '%s' evaluated to null".formatted(spel)
            );
        }

        if (idempotencyKey instanceof UUID uuid) {
            return uuid;
        }

        try {
            return UUID.fromString(idempotencyKey.toString());
        } catch (IllegalArgumentException iae) {
            log.warn("Idempotency key expression '{}' yielded '{}', which is not a UUID", spel, idempotencyKey);
            throw iae;
        }
    }
}
