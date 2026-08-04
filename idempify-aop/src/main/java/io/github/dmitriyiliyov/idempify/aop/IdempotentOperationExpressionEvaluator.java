package io.github.dmitriyiliyov.idempify.aop;

import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotentOperationExpressionEvaluator extends CachedExpressionEvaluator {

    private final Map<ExpressionKey, Expression> keyCache = new ConcurrentHashMap<>();

    /**
     * Evaluates the expression against one intercepted call.
     *
     * @param expression  the expression as written at the call site.
     * @param method      the intercepted method, whose parameters the expression may reference by name.
     * @param targetClass the class of the bean the call landed on, or {@code null} if there is no target.
     * @param target      the bean itself, which the expression sees as its root object.
     * @param args        the arguments of this call.
     * @return whatever the expression evaluated to, possibly {@code null}.
     */
    public Object evaluateIdempotencyKey(String expression,
                                         Method method,
                                         Class<?> targetClass,
                                         Object target,
                                         Object[] args) {
        EvaluationContext evaluationContext = new MethodBasedEvaluationContext(
                target,
                method,
                args,
                getParameterNameDiscoverer()
        );

        AnnotatedElementKey elementKey = new AnnotatedElementKey(method, targetClass);

        return getExpression(keyCache, elementKey, expression).getValue(evaluationContext);
    }
}
