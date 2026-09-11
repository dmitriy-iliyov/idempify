package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method as idempotent, ensuring that it is executed only once for a given idempotency key.
 * <p>
 * This annotation triggers an aspect that intercepts the method call, checks for a unique idempotency key,
 * and stores the result. Subsequent calls with the same key will return the stored result without
 * re-executing the method, until the TTL expires. Only {@code public} methods are matched, and only when
 * reached through the Spring proxy - a call from inside the same bean bypasses the aspect entirely.
 * <p>
 * Every attribute here is optional, and each has a value meaning "not specified at this call site": an empty
 * string, a negative {@link #ttl()}, {@link ConflictHandleStrategyToggle#UNSELECTED},
 * {@link ProcessorTypeToggle#UNSELECTED} and {@link Toggle#UNSELECTED}. What a call site leaves unspecified
 * is decided by the config it names in {@link #config()}, and failing that by the global properties.
 *
 * @see Toggle
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Idempotent {

    String config() default "";

    String idempotencyKey() default "";

    String headerName() default "";

    long ttl() default -1;

    TimeUnit timeUnit() default TimeUnit.HOURS;

    ProcessorTypeToggle processorType() default ProcessorTypeToggle.UNSELECTED;

    ConflictHandleStrategyToggle onConflict() default ConflictHandleStrategyToggle.UNSELECTED;

    Toggle useFingerprint() default Toggle.UNSELECTED;

    Toggle cache4xx() default Toggle.UNSELECTED;

    Toggle cache5xx() default Toggle.UNSELECTED;
}
