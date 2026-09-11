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
    private final ResponseConfig responseConfig;

    private IdempotencyConfig(Builder builder) {
        this.headerName = builder.headerName;
        this.ttl = builder.ttl;
        this.processorType = builder.processorType;
        this.conflictConfig = builder.conflictConfig;
        this.fingerprintConfig = builder.fingerprintConfig;
        this.responseConfig = builder.responseConfig;
    }

    /**
     * Refuses a resolved config whose sections contradict the processor it chose, rather than hand a
     * processor settings it would have to ignore.
     * <p>
     * Both refusals concern {@link ProcessorType#TRANSACTIONAL}, which keeps the operation's record inside
     * the business transaction. A conflict handler has nothing to handle there: a duplicate waits on the
     * store's own insert instead of arriving at a conflict. And keeping an error response is checked against
     * the same constraint, the response being written in the operation's own transaction.
     * <p>
     * Called after a merge rather than from {@link Builder#build()}: a single layer is allowed to be partial,
     * so only the resolved config is worth judging.
     *
     * @throws IllegalStateException if a section contradicts the chosen processor.
     * @throws NullPointerException if no layer decided the processor type.
     */
    public void validate() {
        Objects.requireNonNull(processorType, "processorType cannot be null");
        if (ProcessorType.TRANSACTIONAL.equals(processorType)) {
            if (conflictConfig != null && Boolean.TRUE.equals(conflictConfig.isEnabled())) {
                throw new IllegalStateException("conflictConfig cannot be enabled if processorType is %s".formatted(processorType));
            }

            if (responseConfig != null && shouldCache4xxOr5xx()) {
                throw new IllegalStateException("response with 4xx or 5xx cannot be cached if processorType is %s".formatted(processorType));
            }
        }
        if (conflictConfig != null) {
            conflictConfig.validate();
        }
    }

    private boolean shouldCache4xxOr5xx() {
        return Boolean.TRUE.equals(responseConfig.shouldCache4xx()) || Boolean.TRUE.equals(responseConfig.shouldCache5xx());
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

    public ResponseConfig getResponseConfig() {
        return responseConfig;
    }

    public boolean notEmpty() {
        return headerName != null
                && ttl != null
                && processorType != null
                && conflictConfig != null
                && fingerprintConfig != null
                && responseConfig != null;
    }

    @Override
    public String toString() {
        return "IdempotencyConfig{" +
                "headerName='" + headerName + '\'' +
                ", ttl=" + ttl +
                ", processorType=" + processorType +
                ", conflictConfig=" + conflictConfig +
                ", fingerprintConfig=" + fingerprintConfig +
                ", responseConfig=" + responseConfig +
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

        ResponseConfig responseConfig = target.getResponseConfig();
        if (responseConfig != null && !responseConfig.equals(reference.getResponseConfig())) {
            ResponseConfig referenceResponseConfig = reference.getResponseConfig();
            mergedConfigBuilder.response(referenceResponseConfig == null
                    ? responseConfig
                    : ResponseConfig.merge(referenceResponseConfig, responseConfig)
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
        private ResponseConfig responseConfig;

        private Builder() {}

        private Builder(IdempotencyConfig config) {
            this.headerName = config.headerName;
            this.ttl = config.ttl;
            this.processorType = config.processorType;
            this.conflictConfig = config.conflictConfig;
            this.fingerprintConfig = config.fingerprintConfig;
            this.responseConfig = config.responseConfig;
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

        public Builder response(ResponseConfig responseConfig) {
            this.responseConfig = Objects.requireNonNull(responseConfig, "responseConfig cannot be null");
            return this;
        }

        public Builder response(Consumer<ResponseConfig.Builder> builderConsumer) {
            Objects.requireNonNull(builderConsumer, "builderConsumer cannot be null");
            ResponseConfig.Builder builder = ResponseConfig.builder();
            builderConsumer.accept(builder);
            this.responseConfig = builder.build();
            return this;
        }

        public IdempotencyConfig build() {
            return new IdempotencyConfig(this);
        }
    }
}
