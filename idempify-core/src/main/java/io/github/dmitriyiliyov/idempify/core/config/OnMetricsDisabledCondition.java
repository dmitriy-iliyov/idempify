package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

public class OnMetricsDisabledCondition extends SpringBootCondition {

    private static final String METRICS_MODULE_IDEMPOTENCY_CLASS = "io.github.dmitriyiliyov.idempify.metrics.MetricsIdempotencyEventListener";
    private static final String METRICS_MODULE_CACHE_CLASS = "io.github.dmitriyiliyov.idempify.metrics.MetricsCacheEventListener";
    private static final String METER_REGISTRY_CLASS = "io.micrometer.core.instrument.MeterRegistry";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (isMetricsDisabled(context)) {
            return ConditionOutcome.match("Metrics are disabled");
        }

        String missingClass = firstMissingClass(context.getClassLoader());
        if (missingClass != null) {
            throw new IllegalStateException("""
                Property 'idempify.metrics.enabled' is true, but %s is not on the classpath, so no listener answers 
                the metrics hook. Add the idempify-metrics module together with micrometer-core, or set the property 
                to false
            """.formatted(missingClass));
        }

        return ConditionOutcome.noMatch("Metrics are enabled and the metrics module supplies the listeners");
    }

    private boolean isMetricsDisabled(ConditionContext context) {
        String rawProperty = context.getEnvironment().getProperty("idempify.metrics.enabled");
        if (StringUtils.isBlank(rawProperty)) {
            return true;
        }
        return !Boolean.parseBoolean(rawProperty);
    }

    private String firstMissingClass(ClassLoader classLoader) {
        if (!ClassUtils.isPresent(METRICS_MODULE_IDEMPOTENCY_CLASS, classLoader)) {
            return METRICS_MODULE_IDEMPOTENCY_CLASS;
        }

        if (!ClassUtils.isPresent(METRICS_MODULE_CACHE_CLASS, classLoader)) {
            return METRICS_MODULE_CACHE_CLASS;
        }

        if (!ClassUtils.isPresent(METER_REGISTRY_CLASS, classLoader)) {
            return METER_REGISTRY_CLASS;
        }
        return null;
    }
}
