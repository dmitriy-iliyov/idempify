package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.DelegatingIdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.TypeAwareIdempotentProcessor;

import java.lang.annotation.*;

/**
 * Declares which {@link ProcessorType} a {@link TypeAwareIdempotentProcessor} bean implements, on the
 * {@code @Bean} method that defines it or on the class itself.
 * <p>
 * The type is otherwise known only to a live processor, through {@code getType()}, and asking one means
 * creating it - together with everything it depends on - while bean definitions are still being registered.
 * Declared here, the answer is in the definition, so {@link ConditionalOnMissingProcessorType} reads it
 * without instantiating anything.
 * <p>
 * A processor that leaves this off stays invisible to that condition: the built-in one of the same type is
 * registered as well, and {@link DelegatingIdempotentProcessor} refuses the pair at startup.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProvidesProcessorType {
    ProcessorType type();
}
