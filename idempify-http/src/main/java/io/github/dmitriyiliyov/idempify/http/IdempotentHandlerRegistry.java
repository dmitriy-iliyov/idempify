package io.github.dmitriyiliyov.idempify.http;

import org.springframework.http.server.RequestPath;
import org.springframework.web.method.HandlerMethod;

/**
 * Knows which endpoints are idempotent. Built once from the application's mappings, then read on every
 * request, so lookups happen far more often than registrations.
 */
public interface IdempotentHandlerRegistry {

    /**
     * Returns the handler of the idempotent endpoint that serves this request, or {@code null} if none does.
     * <p>
     * Both halves of the request decide: an endpoint registered for {@code POST} must not answer a {@code GET}
     * on the same path, or a client would be replayed a result it never asked for.
     *
     * @param method      the request method, as the container reports it.
     * @param requestPath the parsed path of the request; only its application-relative part is matched, since
     *                    that is the space the application's own mappings are declared in.
     */
    HandlerMethod getHandlerMethod(String method, RequestPath requestPath);
}
