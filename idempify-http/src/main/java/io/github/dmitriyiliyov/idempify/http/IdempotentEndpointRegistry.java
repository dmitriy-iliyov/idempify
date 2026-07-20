package io.github.dmitriyiliyov.idempify.http;

import java.util.Set;

/**
 * Registry for idempotent endpoints.
 */
public interface IdempotentEndpointRegistry {
    /**
     * Returns a set of URL patterns for idempotent endpoints.
     */
    Set<String> getPatterns();
}
