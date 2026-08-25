package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.config.FingerprintConfig;

import java.util.Objects;

/**
 * Builds the policy the config describes, and nothing more: a setting the config leaves out is a setting no
 * layer decided, and filling it here would put the answer in the last place able to see how it was reached.
 * Whatever a resolved config still leaves open therefore comes back as "this call site does not fingerprint".
 */
public class DefaultFingerprintPolicyProvider implements FingerprintPolicyProvider {

    private final BodyCanonicalizerProvider bodyCanonicalizerProvider;

    public DefaultFingerprintPolicyProvider(BodyCanonicalizerProvider bodyCanonicalizerProvider) {
        this.bodyCanonicalizerProvider = Objects.requireNonNull(bodyCanonicalizerProvider, "bodyCanonicalizerProvider cannot be null");
    }

    @Override
    public FingerprintPolicy provide(FingerprintConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        if (!config.isEnabled()) {
            return null;
        }

        if (config.getFingerprintPolicy() != null) {
            return config.getFingerprintPolicy();
        }

        BodyHandleStrategy strategy = config.getBodyHandleStrategy();
        EmptyBodyFallback fallback = config.getEmptyBodyFallback();
        if (strategy == null || fallback == null) {
            return null;
        }

        return switch (strategy) {
            case RAW_BYTES_HASH -> new RawHashingFingerprintPolicy(fallback);
            case NORMALIZED_BYTES_HASH -> new BytesNormalizingFingerprintPolicy(fallback);
            case CANONICALIZED_BODY_HASH -> canonicalizingPolicy(config, fallback);
        };
    }

    private FingerprintPolicy canonicalizingPolicy(FingerprintConfig config, EmptyBodyFallback fallback) {
        BodyCanonicalizer canonicalizer = config.getBodyCanonicalizer();
        if (canonicalizer != null) {
            return new BodyCanonicalizingFingerprintPolicy(fallback, canonicalizer);
        }

        BodyCanonicalizerConfig canonicalizerConfig = config.getBodyCanonicalizerConfig();
        if (canonicalizerConfig == null) {
            return null;
        }
        return new BodyCanonicalizingFingerprintPolicy(fallback, bodyCanonicalizerProvider.provide(canonicalizerConfig));
    }
}
