package io.github.dmitriyiliyov.idempify.core.request;

/**
 * The incoming request, reduced to the four things idempotency needs: where the key is carried, and what the
 * fingerprint is computed over - path, method and body.
 * <p>
 * The names are HTTP's because HTTP is the only transport implemented so far; another transport maps its own
 * vocabulary onto them.
 */
public interface RequestContext {

    /**
     * Returns which transport this request arrived over, and with it which {@link KeyExtractor} reads its key.
     */
    RequestType getRequestType();

    /**
     * Returns the value carried under this name, or {@code null} if the request carries none.
     */
    String getHeader(String name);

    /**
     * Returns the request method, part of the fingerprint so that the same body sent to the same path by a
     * different verb is not treated as the same request.
     */
    String getMethod();

    /**
     * Returns the request path, part of the fingerprint so that one key reused against another endpoint is
     * not treated as a retry.
     */
    String getPath();

    /**
     * Returns the raw body, or {@code null} if the request has none - in which case the policy's
     * {@code EmptyBodyFallback} decides what is fingerprinted instead.
     */
    byte [] getBodyBytes();
}
