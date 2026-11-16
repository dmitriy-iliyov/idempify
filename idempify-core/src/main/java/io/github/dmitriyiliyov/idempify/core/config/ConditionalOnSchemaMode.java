package io.github.dmitriyiliyov.idempify.core.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * Registers a bean only when {@code idempify.schema.mode} names this {@link SchemaMode}, which is how each
 * migration module declares its own {@link IdempifySchemaMigrator} and only the selected one reaches the
 * context.
 * <p>
 * A value naming no known mode is refused outright rather than quietly leaving the schema unmigrated.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnSchemaModeCondition.class)
@Documented
public @interface ConditionalOnSchemaMode {
    SchemaMode mode();
}
