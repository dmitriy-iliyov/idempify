package io.github.dmitriyiliyov.idempify.core.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * Registers the no-op listener that answers a hook while {@code idempify.metrics.enabled} asks for no
 * metrics, and refuses to start when it asks for metrics nothing on the classpath can deliver.
 * <p>
 * The refusal is about the hook, not about the property: it stands behind {@code @ConditionalOnMissingBean},
 * so an application that answers the hook with a listener of its own is left alone - it asked for metrics
 * and is listening itself, which is not the mistake this refusal is written for. Every hook keeps its own
 * guard, so answering one of them privately does not silence the other.
 * <p>
 * The property is read by {@link OnMetricsDisabledCondition} itself rather than by a
 * {@code @ConditionalOnProperty} standing next to it: conditions on one element are evaluated until the
 * first one declines, and the property one is ordered ahead of any other, so it would answer for the very
 * case the refusal is written for and leave that refusal unreachable.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnMetricsDisabledCondition.class)
@Documented
public @interface ConditionalOnMetricsDisabled { }
