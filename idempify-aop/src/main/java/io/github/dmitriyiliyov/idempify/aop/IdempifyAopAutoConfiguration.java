package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(
        prefix = "idempify",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnClass({Aspect.class, ProceedingJoinPoint.class})
public class IdempifyAopAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentInterceptor idempifyIdempotentInterceptor(KeyExtractor keyExtractor,
                                                               IdempotentProcessor processor) {
        return new DefaultIdempotentInterceptor(keyExtractor, processor);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentOperationExpressionEvaluator idempifyIdempotentOperationExpressionEvaluator() {
        return new IdempotentOperationExpressionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAdvisor idempifyIdempotentAdvisor(IdempotentOperationExpressionEvaluator expressionEvaluator,
                                                       RequestContextProvider requestContextProvider,
                                                       OperationMetadataResolver metadataResolver,
                                                       IdempotentInterceptor interceptor) {
        return new IdempotentAdvisor(expressionEvaluator, requestContextProvider, metadataResolver, interceptor);
    }
}
