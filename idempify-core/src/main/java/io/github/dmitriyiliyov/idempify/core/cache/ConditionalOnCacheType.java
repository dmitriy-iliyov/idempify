package io.github.dmitriyiliyov.idempify.core.cache;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * Registers a bean only when {@code idempify.cache.type} names this {@link CacheType}, which is how each
 * backend declares its own cache wrapper and only the selected one reaches the context.
 * <p>
 * The property is read per bean rather than per module, so a backend module on the classpath contributes
 * nothing until it is chosen. A value naming no known type is refused outright rather than quietly leaving
 * the cache out.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnCacheTypeCondition.class)
@Documented
public @interface ConditionalOnCacheType {
    CacheType type();
}
