package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;

/**
 * Hands out the {@link BodyCanonicalizer} for the format a config names, so that a fingerprint policy never
 * has to know which module supplies it.
 */
public interface BodyCanonicalizerProvider {

    /**
     * Returns a canonicalizer built to this config.
     *
     * @throws IllegalStateException if no creator is registered for the format the config names.
     */
    BodyCanonicalizer provide(BodyCanonicalizerConfig config);
}
