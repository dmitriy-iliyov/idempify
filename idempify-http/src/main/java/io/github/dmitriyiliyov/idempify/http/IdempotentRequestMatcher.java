package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Answers the only question a servlet filter has about an incoming request: is it addressed to an idempotent
 * endpoint, and under which settings.
 * <p>
 * Both halves come back together on purpose. Asking them apart would mean matching the URI twice per request
 * and, worse, would allow the two answers to disagree.
 */
public interface IdempotentRequestMatcher {

    /**
     * Returns the resolved metadata of the endpoint this request is addressed to, or {@code null} if that
     * endpoint is not an idempotent one.
     * <p>
     * Matching nothing is the normal case - most requests of an application are not idempotent - so it is
     * reported as {@code null} rather than as an exception.
     *
     * @param request the request being served, taken whole because matching reads its path the same way the
     *                {@code DispatcherServlet} does rather than off a pre-extracted URI.
     */
    OperationMetadata match(HttpServletRequest request);
}
