package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.fingerprint.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Says how a request is fingerprinted, by picking exactly one level of control:
 * <ul>
 *     <li>{@link #defaults()} - fingerprinting on, nothing overridden;</li>
 *     <li>{@link Builder#fingerprintPolicy} - a whole {@link FingerprintPolicy}, replacing hashing,
 *     comparison and mismatch handling;</li>
 *     <li>{@link Builder#bodyHandleStrategy} - a built-in {@link BodyHandleStrategy}, keeping the default
 *     policy;</li>
 *     <li>{@link Builder#bodyCanonicalizer} - a {@link BodyCanonicalizer}, replacing how the body is
 *     reduced;</li>
 *     <li>{@link Builder#bodyCanonicalizerConfig(BodyCanonicalizerConfig)} - only tuning the default
 *     canonicalizer.</li>
 * </ul>
 * The levels are alternatives, not layers - a hand-written policy need not canonicalize at all, and a
 * hand-written canonicalizer is configured by whoever constructed it. Pick the outermost one you need; the
 * builder rejects a mix of two, so an incoherent config fails at the call site rather than silently losing
 * whichever setting the resolution order happens to drop. The empty-body fallback sits outside the levels
 * and accompanies a strategy or a canonicalizer - on its own it says nothing about how the body is handled,
 * and next to a hand-written policy it would have nobody to read it, so both are rejected.
 */
public final class FingerprintConfig {

    public static final BodyHandleStrategy DEFAULT_BODY_HANDLE_STRATEGY = BodyHandleStrategy.CANONICALIZED_BODY_HASH;
    public static final EmptyBodyFallback DEFAULT_EMPTY_BODY_FALLBACK = new ThrowingEmptyBodyFallback();
    public static final BodyCanonicalizerConfig DEFAULT_BODY_CANONICALIZER_CONFIG = BodyCanonicalizerConfig.defaults();

    private final Boolean enabled;
    private final FingerprintPolicy fingerprintPolicy;
    private final BodyHandleStrategy bodyHandleStrategy;
    private final EmptyBodyFallback emptyBodyFallback;
    private final BodyCanonicalizer bodyCanonicalizer;
    private final BodyCanonicalizerConfig bodyCanonicalizerConfig;

    private FingerprintConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.fingerprintPolicy = builder.fingerprintPolicy;
        this.bodyHandleStrategy = builder.bodyHandleStrategy;
        this.emptyBodyFallback = builder.emptyBodyFallback;
        this.bodyCanonicalizer = builder.bodyCanonicalizer;
        this.bodyCanonicalizerConfig = builder.bodyCanonicalizerConfig;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public FingerprintPolicy getFingerprintPolicy() {
        return fingerprintPolicy;
    }

    public BodyHandleStrategy getBodyHandleStrategy() {
        return bodyHandleStrategy;
    }

    public EmptyBodyFallback getEmptyBodyFallback() {
        return emptyBodyFallback;
    }

    public BodyCanonicalizer getBodyCanonicalizer() {
        return bodyCanonicalizer;
    }

    public BodyCanonicalizerConfig getBodyCanonicalizerConfig() {
        return bodyCanonicalizerConfig;
    }

    public boolean enabledAndEmpty() {
        return enabled &&
                fingerprintPolicy == null &&
                bodyHandleStrategy == null &&
                emptyBodyFallback == null &&
                bodyCanonicalizer == null &&
                bodyCanonicalizerConfig == null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FingerprintConfig that = (FingerprintConfig) o;
        return enabled == that.enabled &&
                Objects.equals(fingerprintPolicy, that.fingerprintPolicy) &&
                bodyHandleStrategy == that.bodyHandleStrategy &&
                Objects.equals(emptyBodyFallback, that.emptyBodyFallback) &&
                Objects.equals(bodyCanonicalizer, that.bodyCanonicalizer) &&
                Objects.equals(bodyCanonicalizerConfig, that.bodyCanonicalizerConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                fingerprintPolicy,
                bodyHandleStrategy,
                emptyBodyFallback,
                bodyCanonicalizer,
                bodyCanonicalizerConfig
        );
    }

    @Override
    public String toString() {
        return "FingerprintConfig{" +
                "enabled=" + enabled +
                ", fingerprintPolicy=" + fingerprintPolicy +
                ", bodyHandleStrategy=" + bodyHandleStrategy +
                ", emptyBodyFallback=" + emptyBodyFallback +
                ", bodyCanonicalizer=" + bodyCanonicalizer +
                ", bodyCanonicalizerConfig=" + bodyCanonicalizerConfig +
                '}';
    }

    public static FingerprintConfig disabled() {
        return builder().enabled(false).build();
    }

    /**
     * The library's own answer to every setting - a filled config, not an abstention. Layered over another
     * one it therefore <em>wins</em>: whoever writes this asks for these values, not for whatever a broader
     * layer chose. To leave the choice open, build an enabled config that sets nothing.
     */
    public static FingerprintConfig defaults() {
        return builder()
                .bodyHandleStrategy(DEFAULT_BODY_HANDLE_STRATEGY)
                .emptyBodyFallback(DEFAULT_EMPTY_BODY_FALLBACK)
                .bodyCanonicalizerConfig(DEFAULT_BODY_CANONICALIZER_CONFIG)
                .build();
    }


    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(FingerprintConfig config) {
        return new Builder(config);
    }

    /**
     * Layers {@code target} over {@code reference}, dropping whatever the reference held for a mechanism the
     * target replaces: a strategy of its own discards a hand-written policy, a byte-level strategy discards
     * canonicalizer settings, and a canonicalizer of its own discards the settings that configured the
     * built-in one.
     * <p>
     * The one combination deliberately left to fail is the opposite direction - canonicalizer settings
     * layered over a hand-written canonicalizer. Settings like {@code includedFields} only mean anything to
     * the built-in canonicalizer, so honouring them would mean silently swapping out the function the broader
     * layer supplied; naming a whole canonicalizer replaces a mechanism, naming settings does not.
     */
    public static FingerprintConfig merge(FingerprintConfig reference, FingerprintConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        if (!target.isEnabled()) {
            return FingerprintConfig.disabled();
        }

        if (target.getFingerprintPolicy() != null) {
            return FingerprintConfig.builder()
                    .fingerprintPolicy(target.getFingerprintPolicy())
                    .build();
        }

        FingerprintConfig.Builder configBuilder = builder(reference);

        BodyHandleStrategy strategy = target.getBodyHandleStrategy();
        if (strategy != null) {
            configBuilder.fingerprintPolicy = null;
            if (!BodyHandleStrategy.CANONICALIZED_BODY_HASH.equals(strategy)) {
                configBuilder.bodyCanonicalizer = null;
                configBuilder.bodyCanonicalizerConfig = null;
            }
            configBuilder.bodyHandleStrategy(strategy);
        }

        EmptyBodyFallback fallback = target.getEmptyBodyFallback();
        if (fallback != null) {
            configBuilder.fingerprintPolicy = null;
            configBuilder.emptyBodyFallback(fallback);
        }

        BodyCanonicalizer canonicalizer = target.getBodyCanonicalizer();
        if (canonicalizer != null) {
            configBuilder.fingerprintPolicy = null;
            configBuilder.bodyCanonicalizerConfig = null;
            configBuilder.bodyCanonicalizer(canonicalizer);
        }

        BodyCanonicalizerConfig canonicalizerConfig = target.getBodyCanonicalizerConfig();
        if (canonicalizerConfig != null) {
            configBuilder.fingerprintPolicy = null;

            BodyCanonicalizerConfig referenceCanonicalizerConfig = reference.getBodyCanonicalizerConfig() == null
                    ? DEFAULT_BODY_CANONICALIZER_CONFIG
                    : reference.getBodyCanonicalizerConfig();
            configBuilder.bodyCanonicalizerConfig(
                    BodyCanonicalizerConfig.merge(referenceCanonicalizerConfig, canonicalizerConfig)
            );
        }

        return configBuilder.build();
    }

    public static final class Builder {

        private boolean enabled = true;
        private FingerprintPolicy fingerprintPolicy;
        private BodyHandleStrategy bodyHandleStrategy;
        private EmptyBodyFallback emptyBodyFallback;
        private BodyCanonicalizer bodyCanonicalizer;
        private BodyCanonicalizerConfig bodyCanonicalizerConfig;

        private Builder() {}

        private Builder(FingerprintConfig config) {
            this.enabled = config.enabled;
            this.fingerprintPolicy = config.fingerprintPolicy;
            this.bodyHandleStrategy = config.bodyHandleStrategy;
            this.emptyBodyFallback = config.emptyBodyFallback;
            this.bodyCanonicalizer = config.bodyCanonicalizer;
            this.bodyCanonicalizerConfig = config.bodyCanonicalizerConfig;
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
            return this;
        }

        public Builder fingerprintPolicy(FingerprintPolicy fingerprintPolicy) {
            this.fingerprintPolicy = Objects.requireNonNull(fingerprintPolicy, "fingerprintPolicy cannot be null");
            return this;
        }

        public Builder bodyHandleStrategy(BodyHandleStrategy bodyHandleStrategy) {
            this.bodyHandleStrategy = Objects.requireNonNull(bodyHandleStrategy, "bodyHandleStrategy cannot be null");
            return this;
        }

        public Builder emptyBodyFallback(EmptyBodyFallback emptyBodyFallback) {
            this.emptyBodyFallback = Objects.requireNonNull(emptyBodyFallback, "emptyBodyFallback cannot be null");
            return this;
        }

        public Builder bodyCanonicalizer(BodyCanonicalizer bodyCanonicalizer) {
            this.bodyCanonicalizer = Objects.requireNonNull(bodyCanonicalizer, "bodyCanonicalizer cannot be null");
            return this;
        }

        public Builder bodyCanonicalizerConfig(BodyCanonicalizerConfig bodyCanonicalizerConfig) {
            this.bodyCanonicalizerConfig = Objects.requireNonNull(bodyCanonicalizerConfig, "bodyCanonicalizerConfig cannot be null");
            return this;
        }

        public Builder bodyCanonicalizerConfig(Consumer<BodyCanonicalizerConfig.Builder> configBuilder) {
            Objects.requireNonNull(configBuilder, "configBuilder cannot be null");
            BodyCanonicalizerConfig.Builder builder = BodyCanonicalizerConfig.builder();
            configBuilder.accept(builder);
            this.bodyCanonicalizerConfig = builder.build();
            return this;
        }

        public FingerprintConfig build() {
            validate();
            if (enabled
                    && bodyHandleStrategy == null
                    && fingerprintPolicy == null
                    && (bodyCanonicalizer != null || bodyCanonicalizerConfig != null)) {
                bodyHandleStrategy = DEFAULT_BODY_HANDLE_STRATEGY;
            }

            return new FingerprintConfig(this);
        }

        private void validate() {
            if (!enabled) {
                validateIfDisabledCarriesSetting();
                return;
            }

            validateIfOnlyPolicySpecified();

            validateIfNotCanonicalizedBodyHashStrategySpecified();

            validateIfCanonicalizerAndCanonicalizerConfigSpecified();

            validateIfOnlyEmptyBodyFallbackSpecified();
        }

        private void validateIfDisabledCarriesSetting() {
            List<String> carried = new ArrayList<>();
            if (fingerprintPolicy != null) {
                carried.add("fingerprintPolicy");
            }
            if (bodyHandleStrategy != null) {
                carried.add("bodyHandleStrategy");
            }
            if (emptyBodyFallback != null) {
                carried.add("emptyBodyFallback");
            }
            if (bodyCanonicalizer != null) {
                carried.add("bodyCanonicalizer");
            }
            if (bodyCanonicalizerConfig != null) {
                carried.add("bodyCanonicalizerConfig");
            }

            if (!carried.isEmpty()) {
                throw new IllegalStateException(
                        "disabled fingerprinting cannot carry any other setting, but carried %s"
                                .formatted(String.join(", ", carried))
                );
            }
        }

        private void validateIfOnlyPolicySpecified() {
            if (fingerprintPolicy != null
                    && (bodyHandleStrategy != null
                    || emptyBodyFallback != null
                    || bodyCanonicalizer != null
                    || bodyCanonicalizerConfig != null)) {
                throw new IllegalStateException(
                        "fingerprintPolicy answers everything this config could say about hashing a body, "
                                + "so it cannot be combined with any other setting"
                );
            }
        }

        private void validateIfNotCanonicalizedBodyHashStrategySpecified() {
            if (bodyHandleStrategy != null
                    && !BodyHandleStrategy.CANONICALIZED_BODY_HASH.equals(bodyHandleStrategy)
                    && (bodyCanonicalizer != null || bodyCanonicalizerConfig != null)) {
                throw new IllegalStateException(
                        "canonicalizer settings apply to %s only, but strategy was %s"
                                .formatted(BodyHandleStrategy.CANONICALIZED_BODY_HASH, bodyHandleStrategy)
                );
            }
        }

        private void validateIfCanonicalizerAndCanonicalizerConfigSpecified() {
            if (bodyCanonicalizer != null && bodyCanonicalizerConfig != null) {
                throw new IllegalStateException(
                        "a hand-written bodyCanonicalizer is configured by whoever built it, so it cannot take a bodyCanonicalizerConfig"
                );
            }
        }

        private void validateIfOnlyEmptyBodyFallbackSpecified() {
            if (emptyBodyFallback != null
                    && fingerprintPolicy == null
                    && bodyHandleStrategy == null
                    && bodyCanonicalizer == null
                    && bodyCanonicalizerConfig == null) {
                throw new IllegalStateException(
                        "emptyBodyFallback says what to hash instead of a missing body, not how a body is handled, "
                                + "so it cannot be the only setting"
                );
            }
        }
    }
}
