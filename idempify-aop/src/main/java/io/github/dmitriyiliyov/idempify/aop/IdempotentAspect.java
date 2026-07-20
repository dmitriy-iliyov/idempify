package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.RequestContextProvider;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

@Aspect
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private final IdempotentInterceptor interceptor;
    private final ExpressionParser expressionParser;
    private final RequestContextProvider requestContextProvider;

    public IdempotentAspect(IdempotentInterceptor interceptor, RequestContextProvider requestContextProvider) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor cannot be null");
        this.requestContextProvider = Objects.requireNonNull(requestContextProvider, "requestContextProvider cannot be null");
        this.expressionParser = new SpelExpressionParser();
    }

    @Pointcut("@annotation(idempotent) && execution(public * *(..))")
    public void pointcut(Idempotent idempotent) { }

    @Around(
            value = "pointcut(annotation)",
            argNames = "jp,annotation"
    )
    public Object advice(ProceedingJoinPoint jp, Idempotent annotation) {
        UUID idempotencyKey = parseIdempotencyKey(jp, annotation);
        return interceptor.intercept(
                new DefaultInterceptContext<>(
                        resolveReturnType(jp),
                        jp::proceed,
                        requestContextProvider.getContext(),
                        idempotencyKey,
                        annotation.headerName(),
                        annotation.ttl(),
                        annotation.timeUnit(),
                        annotation.onConflict(),
                        annotation.conflictHandler(),
                        annotation.useFingerprint(),
                        annotation.fingerprintPolicy()
                )
        );
    }

    @SuppressWarnings("unchecked")
    Class<Object> resolveReturnType(JoinPoint jp) {
        return ((MethodSignature) jp.getSignature()).getReturnType();
    }

    UUID parseIdempotencyKey(JoinPoint jp, Idempotent annotation) {
        String spel = annotation.key();

        if (spel == null || spel.isBlank()) {
            return null;
        }

        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        Object [] args = jp.getArgs();
        EvaluationContext context = new MethodBasedEvaluationContext(
                jp.getTarget(),
                method,
                args,
                new DefaultParameterNameDiscoverer()
        );

        try {
            Object idempotencyKey = expressionParser.parseExpression(spel).getValue(context);

            if (idempotencyKey == null) {
                return null;
            }

            return UUID.fromString((String) idempotencyKey);
        } catch (IllegalArgumentException iae) {
            log.warn("Idempotency key found but have invalid format", iae);
            return null;
        } catch (EvaluationException ee) {
            log.debug("Cannot evaluate SpEL expression '{}'", spel, ee);
            return null;
        }
    }
}
