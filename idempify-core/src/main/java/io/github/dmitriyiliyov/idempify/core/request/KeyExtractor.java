package io.github.dmitriyiliyov.idempify.core.request;

import java.util.UUID;

/**
 * Reads the idempotency key out of an incoming request, in the way its transport carries it.
 * <p>
 * One implementation per {@link RequestType}: the key travels in an HTTP header, in gRPC metadata, in a
 * message property, and only the transport module knows which. Implementations are indexed by
 * {@link #getRequestType()} and picked per call from the context's own type.
 */
public interface KeyExtractor {

    /**
     * Returns the key the request carries.
     *
     * @param headerName the name the key is carried under, as resolved for the call site.
     * @param context    the request to read it from.
     */
    UUID extract(String headerName, RequestContext context);

    /**
     * Returns the transport this extractor reads. Exactly one extractor may claim a given type.
     */
    RequestType getRequestType();
}
