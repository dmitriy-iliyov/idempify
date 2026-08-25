package io.github.dmitriyiliyov.idempify.core.fingerprint;

import java.util.UUID;

/**
 * What {@link FingerprintPolicy#handle} is told about a mismatch: the key that was reused, and the two
 * fingerprints that disagreed.
 * <p>
 * Both fingerprints are hashes, so they name no field of either request - they say that the requests differ,
 * never how. Reporting them is safe; expecting them to explain the difference is not.
 */
public interface FingerprintMismatchContext {

    /**
     * Returns the key both requests carried.
     */
    UUID getIdempotencyKey();

    /**
     * Returns the fingerprint stored when the key was first claimed.
     */
    String getPreviousFingerprint();

    /**
     * Returns the fingerprint of the request that reused the key.
     */
    String getCurrentFingerprint();
}
