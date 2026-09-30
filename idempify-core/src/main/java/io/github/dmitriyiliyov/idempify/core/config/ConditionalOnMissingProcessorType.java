package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.TypeAwareIdempotentProcessor;
import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * Registers the annotated bean only when no other bean already implements this {@link ProcessorType}.
 * <p>
 * {@code @ConditionalOnMissingBean} cannot say that: both built-in processors are declared as
 * {@link TypeAwareIdempotentProcessor}, so a single processor of the application's own would suppress both
 * of them. Which type a bean implements is read off its definition, which is why a bean that means to
 * displace a built-in one has to say so with {@link ProvidesProcessorType}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnMissingProcessorTypeCondition.class)
@Documented
public @interface ConditionalOnMissingProcessorType {
    ProcessorType type();
}
