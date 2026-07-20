package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.RequestContext;

/**
 * Manages the creation, comparison, and handling of fingerprints for idempotent operations.
 */
public interface FingerprintManager {
    /**
     * Generates a fingerprint for the given request context and policy.
     *
     * @param context                the request context.
     * @param fingerprintPolicyClass the class of the fingerprint policy to use.
     * @return the generated fingerprint.
     */
    String generate(RequestContext context,
                    Class<? extends FingerprintPolicy> fingerprintPolicyClass);

    /**
     * Compares two fingerprints using the specified policy.
     *
     * @param previous               the fingerprint of the previous (stored) request.
     * @param current                the fingerprint of the current request.
     * @param fingerprintPolicyClass the class of the fingerprint policy to use.
     * @return true if the fingerprints are equal, false otherwise.
     */
    boolean compareWith(String previous, String current,
                        Class<? extends FingerprintPolicy> fingerprintPolicyClass);

    /**
     * Handles a fingerprint mismatch using the specified policy.
     *
     * @param context                the context of the fingerprint mismatch.
     * @param fingerprintPolicyClass the class of the fingerprint policy to use.
     */
    void handleMismatch(FingerprintMismatchContext context,
                        Class<? extends FingerprintPolicy> fingerprintPolicyClass);
}
