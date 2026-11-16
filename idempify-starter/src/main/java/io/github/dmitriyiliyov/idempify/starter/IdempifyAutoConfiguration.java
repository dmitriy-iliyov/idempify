package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.cache.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.config.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.Objects;

@AutoConfiguration
@ConditionalOnIdempifyEnabled
@EnableConfigurationProperties(IdempifyProperties.class)
public class IdempifyAutoConfiguration {

    @Bean(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME)
    @ConditionalOnMissingBean(name = IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME)
    public IdempotencyConfig idempifyDefaultIdempotencyConfig(IdempifyProperties properties) {
        return properties.provide();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock idempifyClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public CachePropertiesHolder idempifyCachePropertiesHolder(IdempifyProperties properties) {
        return properties.getCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaMigratorPresenceCheck idempifySchemaMigratorPresenceCheck(IdempifyProperties properties,
                                                                           IdempifySchemaMigrator migrator) {
        return new SchemaMigratorPresenceCheck(properties.getSchema().getMode(), migrator);
    }

    public static final class SchemaMigratorPresenceCheck {

        public SchemaMigratorPresenceCheck(SchemaMode mode, IdempifySchemaMigrator migrator) {
            Objects.requireNonNull(mode, "mode cannot be null");
            Objects.requireNonNull(migrator, "migrator cannot be null");
            if (!SchemaMode.NONE.equals(mode) && migrator instanceof NoopIdempifySchemaMigrator) {
                throw new IllegalStateException("""
                        idempify.schema.mode is %s, but no IdempifySchemaMigrator is registered for it: \
                        add the module that migrates the schema in this mode, or set idempify.schema.mode=NONE \
                        and create the schema yourself""".formatted(mode));
            }
        }
    }
}
