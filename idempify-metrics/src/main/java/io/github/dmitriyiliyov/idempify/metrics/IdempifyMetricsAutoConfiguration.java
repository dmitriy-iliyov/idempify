package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.IdempotencyEventListener;
import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.config.ConditionalOnIdempifyEnabled;
import io.github.dmitriyiliyov.idempify.core.config.IdempifyCoreAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = IdempifyCoreAutoConfiguration.class)
@ConditionalOnIdempifyEnabled
@ConditionalOnProperty(
        prefix = "idempify.metrics",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnClass(MeterRegistry.class)
public class IdempifyMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyEventListener idempifyMetricsIdempotencyEventListener(MeterRegistry registry) {
        return new MetricsIdempotencyEventListener(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheEventListener idempifyMetricsCacheEventListener(MeterRegistry registry) {
        return new MetricsCacheEventListener(registry);
    }
}
