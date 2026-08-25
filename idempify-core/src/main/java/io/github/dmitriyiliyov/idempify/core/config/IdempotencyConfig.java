package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.OperationMetadataManager;
import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.RawOperationMetadata;
import io.github.dmitriyiliyov.idempify.core.StringUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A named set of idempotency settings, registered in an {@link IdempotencyConfigRegistry} and referenced by
 * name from a call site (for example {@code @Idempotent(config = "payments")}).
 * <p>
 * A config is a <em>partial</em> description: every getter may return {@code null}, meaning this config does
 * not specify the setting - whatever the builder is not told stays {@code null}.
 * {@link OperationMetadataManager} resolves the effective configuration by layering sources from the most
 * specific to the least: the call site's own {@link RawOperationMetadata}, then this config, then the global
 * properties.
 *
 * @see IdempotencyConfigRegistry
 */
public final class IdempotencyConfig {

    private final String headerName;
    private final Duration ttl;
    private final ProcessorType processorType;
    private final ConflictConfig conflictConfig;
    private final FingerprintConfig fingerprintConfig;
    private final ResponseCacheConfig responseCacheConfig;

    private IdempotencyConfig(Builder builder) {
        this.headerName = builder.headerName;
        this.ttl = builder.ttl;
        this.processorType = builder.processorType;
        this.conflictConfig = builder.conflictConfig;
        this.fingerprintConfig = builder.fingerprintConfig;
        this.responseCacheConfig = builder.responseCacheConfig;
    }

    public void validate() {
        Objects.requireNonNull(processorType, "processorType cannot be null");
        if (ProcessorType.TRANSACTIONAL.equals(processorType)) {
            if (conflictConfig != null && Boolean.TRUE.equals(conflictConfig.isEnabled())) {
                throw new IllegalStateException("conflictConfig cannot be enabled if processorType is %s".formatted(processorType));
            }

            if (responseCacheConfig != null && Boolean.TRUE.equals(responseCacheConfig.isEnabled())) {
                throw new IllegalStateException("responseCacheConfig cannot be enabled if processorType is %s".formatted(processorType));
            }
        }
        if (conflictConfig != null) {
            conflictConfig.validate();
        }
    }

    public String getHeaderName() {
        return headerName;
    }

    public Duration getTtl() {
        return ttl;
    }

    public ProcessorType getProcessorType() {
        return processorType;
    }

    public ConflictConfig getConflictConfig() {
        return conflictConfig;
    }

    public FingerprintConfig getFingerprintConfig() {
        return fingerprintConfig;
    }

    public ResponseCacheConfig getResponseCacheConfig() {
        return responseCacheConfig;
    }

    public boolean notEmpty() {
        return headerName != null
                && ttl != null
                && processorType != null
                && conflictConfig != null
                && fingerprintConfig != null
                && responseCacheConfig != null;
    }

    @Override
    public String toString() {
        return "IdempotencyConfig{" +
                "headerName='" + headerName + '\'' +
                ", ttl=" + ttl +
                ", processorType=" + processorType +
                ", conflictConfig=" + conflictConfig +
                ", fingerprintConfig=" + fingerprintConfig +
                ", responseCacheConfig=" + responseCacheConfig +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(IdempotencyConfig config) {
        return new Builder(config);
    }

    public static IdempotencyConfig merge(IdempotencyConfig reference, IdempotencyConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        if (!reference.notEmpty()) {
            throw new IllegalStateException(
                    "reference must specify every setting a merge can fall back on, but was %s".formatted(reference)
            );
        }

        IdempotencyConfig.Builder mergedConfigBuilder = IdempotencyConfig.builder(reference);

        String headerName = target.getHeaderName();
        if (!StringUtils.isBlank(headerName) && !reference.getHeaderName().equals(headerName)) {
            mergedConfigBuilder.headerName(headerName);
        }

        Duration ttl = target.getTtl();
        if (ttl != null && !ttl.isNegative() && ttl.compareTo(reference.getTtl()) != 0) {
            mergedConfigBuilder.ttl(ttl);
        }

        ProcessorType processorType = target.getProcessorType();
        if (processorType != null && !processorType.equals(reference.getProcessorType())) {
            mergedConfigBuilder.processorType(processorType);
        }

        ConflictConfig conflictConfig = target.getConflictConfig();
        if (conflictConfig != null && !conflictConfig.equals(reference.getConflictConfig())) {
            ConflictConfig referenceConflictConfig = reference.getConflictConfig();
            mergedConfigBuilder.conflict(referenceConflictConfig == null
                    ? conflictConfig
                    : ConflictConfig.merge(referenceConflictConfig, conflictConfig)
            );
        }

        FingerprintConfig fingerprintConfig = target.getFingerprintConfig();
        if (fingerprintConfig != null && !reference.getFingerprintConfig().equals(fingerprintConfig)) {
            mergedConfigBuilder.fingerprint(
                    FingerprintConfig.merge(reference.getFingerprintConfig(), fingerprintConfig)
            );
        }

        ResponseCacheConfig responseCacheConfig = target.getResponseCacheConfig();
        if (responseCacheConfig != null && !responseCacheConfig.equals(reference.getResponseCacheConfig())) {
            ResponseCacheConfig referenceResponseCacheConfig = reference.getResponseCacheConfig();
            mergedConfigBuilder.responseCache(referenceResponseCacheConfig == null
                    ? responseCacheConfig
                    : ResponseCacheConfig.merge(referenceResponseCacheConfig, responseCacheConfig)
            );
        }

        IdempotencyConfig mergedConfig = mergedConfigBuilder.build();
        mergedConfig.validate();
        return mergedConfig;
    }

    public static final class Builder {

        private String headerName;
        private Duration ttl;
        private ProcessorType processorType;
        private ConflictConfig conflictConfig;
        private FingerprintConfig fingerprintConfig;
        private ResponseCacheConfig responseCacheConfig;

        private Builder() {}

        private Builder(IdempotencyConfig config) {
            this.headerName = config.headerName;
            this.ttl = config.ttl;
            this.processorType = config.processorType;
            this.conflictConfig = config.conflictConfig;
            this.fingerprintConfig = config.fingerprintConfig;
            this.responseCacheConfig = config.responseCacheConfig;
        }

        public Builder headerName(String headerName) {
            this.headerName = headerName == null || headerName.isBlank() ? null : headerName.strip();
            return this;
        }

        public Builder ttl(Duration ttl) {
            if (ttl != null && ttl.isNegative()) {
                throw new IllegalArgumentException("ttl cannot be negative");
            }
            this.ttl = ttl;
            return this;
        }

        public Builder ttl(long amount, TimeUnit unit) {
            Objects.requireNonNull(unit, "unit cannot be null");
            return ttl(Duration.of(amount, unit.toChronoUnit()));
        }

        public Builder ttl(long amount, ChronoUnit unit) {
            Objects.requireNonNull(unit, "unit cannot be null");
            return ttl(Duration.of(amount, unit));
        }

        public Builder processorType(ProcessorType processorType) {
            this.processorType = Objects.requireNonNull(processorType, "processorType cannot be null");
            return this;
        }

        public Builder conflict(ConflictConfig conflictConfig) {
            this.conflictConfig = Objects.requireNonNull(conflictConfig, "conflictConfig cannot be null");
            return this;
        }

        public Builder fingerprint(FingerprintConfig fingerprintConfig) {
            Objects.requireNonNull(fingerprintConfig, "fingerprintConfig cannot be null");
            this.fingerprintConfig = fingerprintConfig;
            return this;
        }

        public Builder responseCache(ResponseCacheConfig responseCacheConfig) {
            this.responseCacheConfig = Objects.requireNonNull(responseCacheConfig, "responseCacheConfig cannot be null");
            return this;
        }

        public Builder responseCache(Consumer<ResponseCacheConfig.Builder> builderConsumer) {
            Objects.requireNonNull(builderConsumer, "builderConsumer cannot be null");
            ResponseCacheConfig.Builder builder = ResponseCacheConfig.builder();
            builderConsumer.accept(builder);
            this.responseCacheConfig = builder.build();
            return this;
        }

        public IdempotencyConfig build() {
            return new IdempotencyConfig(this);
        }
    }
}
