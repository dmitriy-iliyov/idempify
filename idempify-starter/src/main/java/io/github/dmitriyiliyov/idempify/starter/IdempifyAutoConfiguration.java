package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.response.CachePropertiesHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(
        prefix = "idempify",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(IdempifyProperties.class)
public class IdempifyAutoConfiguration {

    @Bean(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME)
    @ConditionalOnMissingBean(name = IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME)
    public IdempotencyConfig idempifyDefaultIdempotencyConfig(IdempifyProperties properties) {
        return properties.provide();
    }

    @Bean
    @ConditionalOnMissingBean
    public CachePropertiesHolder idempifyCachePropertiesHolder(IdempifyProperties properties) {
        return properties.getCache();
    }
}
