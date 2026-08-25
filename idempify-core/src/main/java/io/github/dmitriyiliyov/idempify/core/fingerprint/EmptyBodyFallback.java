package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

/**
 * Decides what a fingerprint policy hashes when the request carries no body.
 */
public interface EmptyBodyFallback {

    /**
     * Returns the bytes to fingerprint in place of the missing body, an empty array to leave the body out of
     * the fingerprint entirely, or throws to reject the request outright.
     * <p>
     * Must never return {@code null}: unlike an empty array, which says "nothing to add", it says nothing at
     * all and only fails further down.
     */
    byte [] fallback(RequestContext context);

    /**
     * Leaves a body-less request to be fingerprinted by its path and method alone - the right answer for an
     * endpoint that legitimately takes no body, where rejecting the request would be rejecting its normal
     * shape.
     * <p>
     * It buys tolerance at the usual price: two body-less requests to the same path and method fingerprint
     * the same, so a key reused between them is replayed rather than reported as a mismatch.
     */
    EmptyBodyFallback NOOP = context -> new byte[0];
}
