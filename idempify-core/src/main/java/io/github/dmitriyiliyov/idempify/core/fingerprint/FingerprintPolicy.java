package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.RequestContext;

/**
 * Defines the policy for generating and comparing request fingerprints.
 * A fingerprint is a unique representation of the request, used to ensure that duplicate requests have the same content.
 */
public interface FingerprintPolicy {
    /**
     * Generates a fingerprint for the given request context.
     *
     * @param context the request context.
     * @return the generated fingerprint.
     */
    String generate(RequestContext context);

    /**
     * Compares two fingerprints to determine if they are equal.
     *
     * @param previous the fingerprint of the previous request.
     * @param current  the fingerprint of the current request.
     * @return true if the fingerprints are equal, false otherwise.
     */
    boolean compare(String previous, String current);

    /**
     * Handles a fingerprint mismatch.
     * This method is called when the fingerprint of the current request does not match the fingerprint of the original request.
     *
     * @param context the context of the fingerprint mismatch.
     */
    void handle(FingerprintMismatchContext context);
}
