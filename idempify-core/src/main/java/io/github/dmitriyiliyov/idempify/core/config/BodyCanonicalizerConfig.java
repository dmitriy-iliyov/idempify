package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tunes how a {@link BodyCanonicalizer} reduces a request body to the string that gets fingerprinted.
 * <p>
 * A <em>partial</em> description: {@link #getFormat()} and {@link #getCanonicalizeStrategy()} may return
 * {@code null}, meaning this layer does not decide the setting. The field sets are the exception - an empty
 * set already means "no restriction", so they need no third state.
 */
public final class BodyCanonicalizerConfig {

    public static final BodyFormat DEFAULT_FORMAT = BodyFormat.valueOf(IdempifyDefaults.BODY_FORMAT_VALUE);
    public static final CanonicalizeStrategy DEFAULT_CANONICALIZE_STRATEGY =
            CanonicalizeStrategy.valueOf(IdempifyDefaults.CANONICALIZE_STRATEGY_VALUE);
    public static final Set<String> DEFAULT_INCLUDED_FIELDS = Set.of();
    public static final Set<String> DEFAULT_EXCLUDED_FIELDS = Set.of();

    private final BodyFormat format;
    private final CanonicalizeStrategy canonicalizeStrategy;
    private final Set<String> includedFields;
    private final Set<String> excludedFields;

    private BodyCanonicalizerConfig(Builder builder) {
        this.format = builder.format;
        this.canonicalizeStrategy = builder.canonicalizeStrategy;
        boolean hasIncludedFields = builder.includedFields != null && !builder.includedFields.isEmpty();
        boolean hasExcludedFields = builder.excludedFields != null && !builder.excludedFields.isEmpty();
        if (hasIncludedFields && hasExcludedFields) {
            throw new IllegalStateException(
                    """
                        includedFields and excludedFields select the body in opposite ways and cannot be combined, 
                        but includedFields=%s and excludedFields=%s were both given
                    """.formatted(builder.includedFields, builder.excludedFields)
            );
        }
        this.includedFields = builder.includedFields == null ? DEFAULT_INCLUDED_FIELDS : builder.includedFields;
        this.excludedFields = builder.excludedFields == null ? DEFAULT_EXCLUDED_FIELDS : builder.excludedFields;
    }

    public BodyFormat getFormat() {
        return this.format;
    }

    public CanonicalizeStrategy getCanonicalizeStrategy() {
        return canonicalizeStrategy;
    }

    public Set<String> getIncludedFields() {
        return includedFields;
    }

    public Set<String> getExcludedFields() {
        return excludedFields;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BodyCanonicalizerConfig that = (BodyCanonicalizerConfig) o;
        return format == that.format &&
                canonicalizeStrategy == that.canonicalizeStrategy &&
                Objects.equals(includedFields, that.includedFields) &&
                Objects.equals(excludedFields, that.excludedFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, canonicalizeStrategy, includedFields, excludedFields);
    }

    @Override
    public String toString() {
        return "BodyCanonicalizerConfig{" +
                "format=" + format +
                ", canonicalizeStrategy=" + canonicalizeStrategy +
                ", includedFields=" + includedFields +
                ", excludedFields=" + excludedFields +
                '}';
    }

    public static BodyCanonicalizerConfig defaults() {
        return builder()
                .format(DEFAULT_FORMAT)
                .canonicalizeStrategy(DEFAULT_CANONICALIZE_STRATEGY)
                .build();
    }


    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(BodyCanonicalizerConfig config) {
        return new Builder(config);
    }

    /**
     * Layers {@code target} over {@code reference}: a setting the target decided wins, one it left
     * {@code null} keeps the reference's value. That is why the {@code DEFAULT_*} constants are applied by
     * {@link #defaults()} and not by the builder - a target left at the builder's defaults would otherwise
     * override the reference with settings nobody chose.
     * <p>
     * The two field preferences move together, since they exclude each other: a target naming any field
     * replaces the reference's pair as a whole, so a call site can switch from the inherited
     * {@code excludedFields} to its own {@code includedFields} instead of building a config that fails.
     */
    public static BodyCanonicalizerConfig merge(BodyCanonicalizerConfig reference, BodyCanonicalizerConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        BodyCanonicalizerConfig.Builder configBuilder = builder(reference);

        BodyFormat format = target.getFormat();
        if (format != null) {
            configBuilder.format(format);
        }

        CanonicalizeStrategy canonicalizeStrategy = target.getCanonicalizeStrategy();
        if (canonicalizeStrategy != null) {
            configBuilder.canonicalizeStrategy(canonicalizeStrategy);
        }

        Set<String> includedFields = target.getIncludedFields();
        Set<String> excludedFields = target.getExcludedFields();
        if (!includedFields.isEmpty() || !excludedFields.isEmpty()) {
            configBuilder.includedFields(includedFields)
                    .excludedFields(excludedFields);
        }

        return configBuilder.build();
    }

    public static final class Builder {

        private BodyFormat format;
        private CanonicalizeStrategy canonicalizeStrategy;
        private Set<String> includedFields;
        private Set<String> excludedFields;

        private Builder() {}

        private Builder(BodyCanonicalizerConfig config) {
            this.format = config.format;
            this.canonicalizeStrategy = config.canonicalizeStrategy;
            this.includedFields = config.includedFields;
            this.excludedFields = config.excludedFields;
        }

        public Builder format(BodyFormat format) {
            this.format = Objects.requireNonNull(format, "format cannot be null");
            return this;
        }

        public Builder canonicalizeStrategy(CanonicalizeStrategy canonicalizeStrategy) {
            this.canonicalizeStrategy = Objects.requireNonNull(canonicalizeStrategy, "canonicalizeStrategy cannot be null");
            return this;
        }

        /**
         * Blank names are dropped, so a set that is {@code null}, empty or left with nothing after stripping
         * lifts the restriction and lets every field take part in the fingerprint.
         */
        public Builder includedFields(Set<String> includedFields) {
            if (includedFields == null) {
                this.includedFields = null;
                return this;
            }
            Set<String> stripped = includedFields.stream()
                    .filter(Objects::nonNull)
                    .map(String::strip)
                    .filter(field -> !field.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            this.includedFields = Set.copyOf(stripped);
            return this;
        }

        /**
         * Varargs shorthand for {@link #includedFields(Set)}, with the same handling of blank names.
         */
        public Builder includedFields(String ... includedFields) {
            if (includedFields == null) {
                this.includedFields = null;
                return this;
            }
            return includedFields(new LinkedHashSet<>(Arrays.asList(includedFields)));
        }

        public Builder excludedFields(Set<String> excludedFields) {
            if (excludedFields == null) {
                this.excludedFields = null;
                return this;
            }
            Set<String> stripped = excludedFields.stream()
                    .filter(Objects::nonNull)
                    .map(String::strip)
                    .filter(field -> !field.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            this.excludedFields = Set.copyOf(stripped);
            return this;
        }

        public Builder excludedFields(String ... excludedFields) {
            if (excludedFields == null) {
                this.excludedFields = null;
                return this;
            }
            return excludedFields(new LinkedHashSet<>(Arrays.asList(excludedFields)));
        }
        public BodyCanonicalizerConfig build() {
            return new BodyCanonicalizerConfig(this);
        }
    }
}
