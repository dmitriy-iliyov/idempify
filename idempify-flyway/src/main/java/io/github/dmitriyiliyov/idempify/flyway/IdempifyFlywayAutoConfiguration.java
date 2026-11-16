package io.github.dmitriyiliyov.idempify.flyway;

import io.github.dmitriyiliyov.idempify.core.config.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Declares the Flyway migrator when {@code idempify.schema.mode} asks for it, ahead of the core so that the
 * core's no-op migrator steps aside.
 */
@AutoConfiguration(before = IdempifyCoreAutoConfiguration.class)
@ConditionalOnIdempifyEnabled
@ConditionalOnClass(Flyway.class)
@ConditionalOnSchemaMode(mode = SchemaMode.FLYWAY)
public class IdempifyFlywayAutoConfiguration {

    @Bean(initMethod = "migrate")
    @ConditionalOnMissingBean
    public IdempifySchemaMigrator idempifyFlywaySchemaMigrator(DataSource dataSource,
                                                               IdempifySchemaLocation schemaLocation) {
        return new FlywayIdempifySchemaMigrator(dataSource, schemaLocation);
    }
}
