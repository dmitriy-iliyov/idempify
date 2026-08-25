package io.github.dmitriyiliyov.idempify.core;

/**
 * An {@link IdempotentProcessor} that names the {@link ProcessorType} it implements, so the registered
 * processors can be indexed by type at startup and each call routed to the one its metadata asks for.
 */
public interface TypeAwareIdempotentProcessor extends IdempotentProcessor {

    /**
     * Returns the type this processor implements. Exactly one processor may claim a given type.
     */
    ProcessorType getType();
}
