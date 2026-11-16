package io.github.dmitriyiliyov.idempify.core.config;

import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;

import java.util.Set;

/**
 * Tells Spring Boot that an {@link IdempifySchemaMigrator} initializes the database, so every bean that
 * depends on an initialized database is created after it.
 * <p>
 * The order puts it behind the application's own Flyway, whose detector answers with 1: the library migrates
 * a schema the application has already brought up.
 */
public final class IdempifySchemaMigratorDatabaseInitializerDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

    @Override
    protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
        return Set.of(IdempifySchemaMigrator.class);
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
