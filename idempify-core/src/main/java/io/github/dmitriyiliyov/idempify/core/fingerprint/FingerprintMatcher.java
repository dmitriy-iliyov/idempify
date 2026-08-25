package io.github.dmitriyiliyov.idempify.core.fingerprint;

import java.util.UUID;

/**
 * Checks the fingerprint of the request being handled against the one stored when the key was first claimed,
 * and reacts if they disagree.
 * <p>
 * Kept apart from {@link FingerprintPolicy} because the check has two callers - the core manager before a
 * replay, and the http filter before answering from cache - and what happens on a mismatch must not differ
 * between them. The policy still owns both the comparison and the reaction; this only sequences them and
 * reports the event.
 */
public interface FingerprintMatcher {

    /**
     * Returns normally when the fingerprints agree; otherwise hands the mismatch to the policy, which decides
     * whether that is fatal.
     *
     * @param current        the fingerprint computed for the request being handled.
     * @param previous       the fingerprint stored when the key was claimed.
     * @param policy         the policy of this call site.
     * @param idempotencyKey the key both requests carry, reported in whatever the policy raises.
     */
    void match(String current, String previous, FingerprintPolicy policy, UUID idempotencyKey);
}
