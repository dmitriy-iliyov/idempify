package io.github.dmitriyiliyov.idempify.core.cache;

import io.github.dmitriyiliyov.idempify.core.config.AnnotationAttributeUtils;
import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnCacheTypeCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        CacheType annotationCacheType = getAnnotationCacheType(metadata);
        CacheType envCacheType = getEnvCacheType(context);

        if (annotationCacheType.equals(envCacheType)) {
            return ConditionOutcome.match();
        }
        return ConditionOutcome.noMatch("Cache type from environment not match with annotation");
    }

    private CacheType getAnnotationCacheType(AnnotatedTypeMetadata metadata) {
        String annotationClassName = ConditionalOnCacheType.class.getName();
        return (CacheType) AnnotationAttributeUtils.getAttribute(annotationClassName, "type", metadata);
    }

    private CacheType getEnvCacheType(ConditionContext context) {
        String currentRawCacheType = context.getEnvironment().getProperty("idempify.cache.type");

        if (currentRawCacheType == null) {
            return CacheType.fromStr(IdempifyDefaults.CACHE_TYPE_VALUE);
        }

        return CacheType.fromStr(currentRawCacheType);
    }
}
