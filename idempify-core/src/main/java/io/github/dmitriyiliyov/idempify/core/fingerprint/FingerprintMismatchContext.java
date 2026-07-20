package io.github.dmitriyiliyov.idempify.core.fingerprint;

import java.util.UUID;

/**
 * Provides context for a fingerprint mismatch.
 * A fingerprint mismatch occurs when a new request with a given idempotency key has a different fingerprint than the original request.
 */
public interface FingerprintMismatchContext {
    /**
     * Returns the idempotency key of the request.
     */
    UUID getIdempotencyKey();

    /**
     * Returns the fingerprint of the previous request.
     */
    String getPreviousFingerprint();

    /**
     * Returns the fingerprint of the current request.
     */
    String getCurrentFingerprint();
}
