package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.time.Duration;
import java.util.Objects;

/**
 * The resolved settings of one call site, as {@link OperationMetadataManager} hands them to a processor.
 * <p>
 * Where the {@link IdempotencyConfig} it is built from is partial - every setting may be left out for a more
 * specific layer to decide - this is the end of that layering, so {@link Builder#build()} refuses anything a
 * processor would still have to decide for itself.
 * <p>
 * Two settings are exempt, because for them "absent" is itself an answer rather than an unfinished decision:
 * the fingerprint policy and the conflict handler are {@code null} when the call site does not fingerprint and
 * does not handle conflicts. Neither gets a do-nothing stand-in - both are asked for behind a guard
 * ({@link #useFingerprint()}, and the state of the operation for the handler), so a stand-in would only ever
 * run where the guard was forgotten, and there it would hide the mistake instead of surfacing it.
 * <p>
 * The response cache config is not one of the two, even though it can also say "no": it says it as
 * {@link ResponseConfig#disabled()}, a value of its own, so a {@code null} there is a decision nobody
 * made rather than a decision to cache nothing - and callers read it without a guard.
 */
public final class DefaultOperationMetadata implements OperationMetadata {

    private final String headerName;
    private final Duration ttl;
    private final ProcessorType processorType;
    private final ConflictHandler conflictHandler;
    private final FingerprintPolicy fingerprintPolicy;
    private final ResponseConfig responseConfig;

    private DefaultOperationMetadata(Builder builder) {
        this.headerName = builder.headerName;
        this.ttl = builder.ttl;
        this.processorType = builder.processorType;
        this.conflictHandler = builder.conflictHandler;
        this.fingerprintPolicy = builder.fingerprintPolicy;
        this.responseConfig = builder.responseConfig;
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public Duration getTtl() {
        return ttl;
    }

    @Override
    public ProcessorType getProcessorType() {
        return processorType;
    }

    @Override
    public ConflictHandler getConflictHandler() {
        return conflictHandler;
    }

    @Override
    public FingerprintPolicy getFingerprintPolicy() {
        return fingerprintPolicy;
    }

    @Override
    public ResponseConfig getResponseConfig() {
        return responseConfig;
    }

    @Override
    public String toString() {
        return "DefaultOperationMetadata{" +
                "headerName='" + headerName + '\'' +
                ", ttl=" + ttl +
                ", processorType=" + processorType +
                ", conflictHandler=" + conflictHandler +
                ", fingerprintPolicy=" + fingerprintPolicy +
                ", responseConfig=" + responseConfig +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String headerName;
        private Duration ttl;
        private ProcessorType processorType;
        private ConflictHandler conflictHandler;
        private FingerprintPolicy fingerprintPolicy;
        private ResponseConfig responseConfig;

        private Builder() {}

        /**
         * A {@code null} name is the answer of a call site that takes its key from an expression, so it is
         * accepted and read back through {@link OperationMetadata#useHeaderName()}. A blank one names nothing
         * and is refused.
         */
        public Builder headerName(String headerName) {
            if (headerName == null) {
                this.headerName = null;
                return this;
            }
            if (StringUtils.isBlank(headerName)) {
                throw new IllegalArgumentException("headerName cannot be blank");
            }
            this.headerName = headerName.strip();
            return this;
        }

        public Builder ttl(Duration ttl) {
            Objects.requireNonNull(ttl, "ttl cannot be null");
            if (ttl.isNegative()) {
                throw new IllegalArgumentException("ttl cannot be negative");
            }
            this.ttl = ttl;
            return this;
        }

        public Builder processorType(ProcessorType processorType) {
            this.processorType = Objects.requireNonNull(processorType, "processorType cannot be null");
            return this;
        }

        public Builder conflictHandler(ConflictHandler conflictHandler) {
            this.conflictHandler = conflictHandler;
            return this;
        }

        public Builder fingerprintPolicy(FingerprintPolicy fingerprintPolicy) {
            this.fingerprintPolicy = fingerprintPolicy;
            return this;
        }

        public Builder responseConfig(ResponseConfig responseConfig) {
            this.responseConfig = Objects.requireNonNull(responseConfig, "responseConfig cannot be null");
            return this;
        }

        public DefaultOperationMetadata build() {
            Objects.requireNonNull(ttl, "ttl cannot be null");
            Objects.requireNonNull(processorType, "processorType cannot be null");
            Objects.requireNonNull(responseConfig, "responseConfig cannot be null");

            if (ProcessorType.TRANSACTIONAL.equals(processorType) &&
                    (responseConfig.shouldCache4xx() || responseConfig.shouldCache5xx())) {
                throw new IllegalStateException("""
                        if processorType is %s responses with 4xx or 5xx cannot be cached: the operation record 
                        is rolled back together with the business logic, so a cached result would outlive it
                """.formatted(processorType));
            }

            return new DefaultOperationMetadata(this);
        }
    }
}
