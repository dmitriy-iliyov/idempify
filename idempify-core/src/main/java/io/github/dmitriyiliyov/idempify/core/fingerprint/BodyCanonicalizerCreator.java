package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;

/**
 * Builds a {@link BodyCanonicalizer} for one body format, tuned to the call site being resolved.
 * <p>
 * A factory rather than a bean because the canonicalizer depends on both ends at once: on settings that
 * belong to a single call site, which rules out one shared instance, and on a parser the core cannot see,
 * which rules out building it there. A module that can read a format contributes one implementation of this.
 */
public interface BodyCanonicalizerCreator {

    BodyCanonicalizer create(BodyCanonicalizerConfig config);

    /**
     * Returns the format this creator handles. Exactly one creator may claim a given format.
     */
    BodyFormat getFormat();
}
