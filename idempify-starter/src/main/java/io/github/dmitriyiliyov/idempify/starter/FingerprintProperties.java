package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.config.FingerprintConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;
import java.util.Set;

/**
 * Holds the {@code idempify.fingerprint.*} properties - the switch, the strategy for reducing a request body
 * to what gets hashed, what to do when the body is empty, and the canonicalizer's own block.
 * <p>
 * The line these properties stop at is the one {@code FingerprintConfig} draws: a whole {@code
 * FingerprintPolicy} or a hand-written {@code BodyCanonicalizer} is an instance, and an instance cannot be
 * named in YAML - a call site that needs one supplies it on the config it points at.
 */
public final class FingerprintProperties {

    private final Boolean enabled;
    private final BodyHandleStrategy strategy;
    private final EmptyBodyFallbackStrategy emptyBodyFallback;
    @NestedConfigurationProperty
    private final BodyCanonicalizerProperties canonicalizer;

    public FingerprintProperties(@DefaultValue("true") Boolean enabled,
                                 @DefaultValue("CANONICALIZED_BODY_HASH") BodyHandleStrategy strategy,
                                 @DefaultValue("THROWING") EmptyBodyFallbackStrategy emptyBodyFallback,
                                 BodyCanonicalizerProperties canonicalizer) {
        this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy cannot be null");
        this.emptyBodyFallback = Objects.requireNonNull(emptyBodyFallback, "emptyBodyFallback cannot be null");
        this.canonicalizer = canonicalizer;
    }

    public FingerprintConfig toFingerprintConfig() {
        if (!enabled) {
            return FingerprintConfig.disabled();
        }

        FingerprintConfig.Builder configBuilder = FingerprintConfig.builder()
                .enabled(enabled)
                .bodyHandleStrategy(strategy)
                .emptyBodyFallback(resolveEmptyBodyFallback(emptyBodyFallback));

        if (BodyHandleStrategy.CANONICALIZED_BODY_HASH.equals(strategy)) {
            configBuilder.bodyCanonicalizerConfig(
                    canonicalizer == null
                    ? BodyCanonicalizerConfig.defaults()
                    : canonicalizer.toBodyCanonicalizerConfig()
            );
        } else {
            if (canonicalizer != null) {
                throw new IllegalStateException("canonicalizer should not be specified when strategy is not %s"
                        .formatted(BodyHandleStrategy.CANONICALIZED_BODY_HASH));
            }
        }

        return configBuilder.build();
    }

    private EmptyBodyFallback resolveEmptyBodyFallback(EmptyBodyFallbackStrategy strategy) {
        return switch (strategy) {
            case NOOP -> EmptyBodyFallback.NOOP;
            case THROWING -> new ThrowingEmptyBodyFallback();
        };
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public EmptyBodyFallbackStrategy getEmptyBodyFallback() {
        return emptyBodyFallback;
    }

    public BodyHandleStrategy getStrategy() {
        return strategy;
    }

    public BodyCanonicalizerProperties getCanonicalizer() {
        return canonicalizer;
    }

    @Override
    public String toString() {
        return "FingerprintProperties{" +
                "enabled=" + enabled +
                ", strategy=" + strategy +
                ", emptyBodyFallback=" + emptyBodyFallback +
                ", canonicalizer=" + canonicalizer +
                '}';
    }

    public enum EmptyBodyFallbackStrategy {
        THROWING, NOOP
    }

    public static final class BodyCanonicalizerProperties {

        private final BodyFormat format;
        private final CanonicalizeStrategy strategy;
        private final Set<String> includedFields;
        private final Set<String> excludedFields;
        private final BodyCanonicalizerConfig config;

        public BodyCanonicalizerProperties(@DefaultValue("JSON") BodyFormat format,
                                           @DefaultValue("LEXICOGRAPHICAL") CanonicalizeStrategy strategy,
                                           @DefaultValue Set<String> includedFields,
                                           @DefaultValue Set<String> excludedFields) {
            this.format = Objects.requireNonNull(format, "format cannot be null");
            this.strategy = Objects.requireNonNull(strategy, "strategy cannot be null");
            this.includedFields = Objects.requireNonNull(includedFields, "includedFields cannot be null");
            this.excludedFields = Objects.requireNonNull(excludedFields, "excludedFields cannot be null");
            this.config = BodyCanonicalizerConfig.builder()
                    .format(format)
                    .canonicalizeStrategy(strategy)
                    .includedFields(includedFields)
                    .excludedFields(excludedFields)
                    .build();
        }

        public BodyCanonicalizerConfig toBodyCanonicalizerConfig() {
            return config;
        }

        public BodyFormat getFormat() {
            return format;
        }

        public CanonicalizeStrategy getStrategy() {
            return strategy;
        }

        public Set<String> getIncludedFields() {
            return includedFields;
        }

        public Set<String> getExcludedFields() {
            return excludedFields;
        }

        @Override
        public String toString() {
            return "BodyCanonicalizerProperties{" +
                    "format=" + format +
                    ", strategy=" + strategy +
                    ", includedFields=" + includedFields +
                    ", excludedFields=" + excludedFields +
                    '}';
        }
    }
}
