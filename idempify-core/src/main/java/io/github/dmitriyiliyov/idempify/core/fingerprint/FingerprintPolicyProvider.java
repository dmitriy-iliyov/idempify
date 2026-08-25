package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.config.FingerprintConfig;

/**
 * Turns the {@link FingerprintConfig} of a call into the {@link FingerprintPolicy} that will fingerprint it.
 * <p>
 * The config expresses one of several levels of control - a ready-made policy, a built-in
 * {@link BodyHandleStrategy}, a {@link BodyCanonicalizer}, or only canonicalization settings - and
 * this is the one place that knows how to reduce any of them to a single policy instance.
 */
public interface FingerprintPolicyProvider {

    /**
     * Returns the policy to fingerprint with, or {@code null} when the config has fingerprinting turned off -
     * absence is the answer there, and a do-nothing policy would only let a caller that skipped its
     * {@code useFingerprint} guard proceed as if it had fingerprinted.
     */
    FingerprintPolicy provide(FingerprintConfig config);
}
