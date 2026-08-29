package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.ConditionalOnIdempifyEnabled;
import io.github.dmitriyiliyov.idempify.core.IdempifyCoreAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.IdempotencyEventListener;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCacheWrapper;
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
    public IdempotencyEventListener idempifyIdempotencyEventListener(MeterRegistry registry) {
        return new MicrometerIdempotencyEventListener(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseCacheWrapper idempifyMetricsResponseCacheWrapper(MeterRegistry registry) {
        return new ResponseCacheWrapper() {
            @Override
            public ResponseCache wrap(ResponseCache responseCache) {
                return new MetricsResponseCacheDecorator(responseCache, registry);
            }

            @Override
            public int getPriority() {
                return Integer.MAX_VALUE;
            }
        };
    }
}
