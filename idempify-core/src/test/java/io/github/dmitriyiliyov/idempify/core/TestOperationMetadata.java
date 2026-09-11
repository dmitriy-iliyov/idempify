package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.time.Duration;

/**
 * A lenient {@link OperationMetadata} for core tests: unlike {@link DefaultOperationMetadata} it demands
 * nothing, so a test names only the settings its own scenario turns on.
 */
public final class TestOperationMetadata implements OperationMetadata {

    private final String headerName;
    private final Duration ttl;
    private final ConflictHandler conflictHandler;
    private final FingerprintPolicy fingerprintPolicy;
    private final ResponseConfig responseConfig;
    private final ProcessorType processorType;

    private TestOperationMetadata(Builder builder) {
        this.headerName = builder.headerName;
        this.ttl = builder.ttl;
        this.conflictHandler = builder.conflictHandler;
        this.fingerprintPolicy = builder.fingerprintPolicy;
        this.responseConfig = builder.responseConfig;
        this.processorType = builder.processorType;
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
    public ProcessorType getProcessorType() {
        return processorType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String headerName = IdempifyDefaults.HEADER_NAME;
        private Duration ttl = Duration.parse(IdempifyDefaults.TTL_VALUE);
        private ConflictHandler conflictHandler;
        private FingerprintPolicy fingerprintPolicy;
        private ResponseConfig responseConfig = ResponseConfig.defaults();
        private ProcessorType processorType = ProcessorType.TRANSACTIONAL;

        private Builder() {}

        public Builder headerName(String headerName) {
            this.headerName = headerName;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
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

        public Builder responseCacheConfig(ResponseConfig responseConfig) {
            this.responseConfig = responseConfig;
            return this;
        }

        public Builder processorType(ProcessorType processorType) {
            this.processorType = processorType;
            return this;
        }

        public TestOperationMetadata build() {
            return new TestOperationMetadata(this);
        }
    }
}
