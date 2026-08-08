package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({Aspect.class, ProceedingJoinPoint.class})
public class IdempifyAopAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentInterceptor idempotentInterceptor(KeyExtractor keyExtractor,
                                                       IdempotentProcessor processor) {
        return new DefaultIdempotentInterceptor(keyExtractor, processor);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationMetadataCache operationMetadataCache() {
        return new DefaultOperationMetadataCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationMetadataResolver operationMetadataResolver(OperationMetadataCache cache,
                                                              OperationMetadataManager manager) {
        return new DefaultOperationMetadataResolver(cache, manager);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentOperationExpressionEvaluator idempotentOperationExpressionEvaluator() {
        return new IdempotentOperationExpressionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(IdempotentOperationExpressionEvaluator expressionEvaluator,
                                             RequestContextProvider requestContextProvider,
                                             OperationMetadataResolver metadataResolver,
                                             IdempotentInterceptor interceptor) {
        return new IdempotentAspect(expressionEvaluator, requestContextProvider, metadataResolver, interceptor);
    }
}
