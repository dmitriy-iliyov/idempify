package io.github.dmitriyiliyov.idempify.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

/**
 * The single switch every module hangs on: {@code idempify.enabled}, on unless the application says
 * otherwise.
 * <p>
 * Written once here rather than repeated as a {@code @ConditionalOnProperty} on each auto-configuration, so
 * that switching the library off cannot half-work - a module that spelled the property differently would keep
 * contributing beans to an application that asked for none.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(
        prefix = "idempify",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Documented
public @interface ConditionalOnIdempifyEnabled { }
