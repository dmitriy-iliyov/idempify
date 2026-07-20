package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Defines the contract for extracting an idempotency key from a request.
 */
public interface KeyExtractor {
    /**
     * Extracts the idempotency key from the given request context.
     *
     * @param headerName the name of the header or metadata property that contains the idempotency key.
     * @param context    the request context.
     * @return the extracted idempotency key.
     */
    UUID extract(String headerName, RequestContext context);

    /**
     * Returns the type of request that this extractor can handle.
     */
    RequestType getRequestType();
}
