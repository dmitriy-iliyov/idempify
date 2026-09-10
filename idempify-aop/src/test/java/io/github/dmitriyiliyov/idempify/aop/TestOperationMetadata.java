package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.time.Duration;

final class TestOperationMetadata implements OperationMetadata {

    private final String headerName;
    private final Duration ttl;
    private final ProcessorType processorType;
    private final ConflictHandler conflictHandler;
    private final boolean useFingerprint;
    private final FingerprintPolicy fingerprintPolicy;
    private final ResponseConfig responseConfig;

    private TestOperationMetadata(Builder builder) {
        this.headerName = builder.headerName;
        this.ttl = builder.ttl;
        this.processorType = builder.processorType;
        this.conflictHandler = builder.conflictHandler;
        this.useFingerprint = builder.useFingerprint;
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
    public boolean useFingerprint() {
        return useFingerprint;
    }

    @Override
    public FingerprintPolicy getFingerprintPolicy() {
        return fingerprintPolicy;
    }

    @Override
    public ResponseConfig getResponseConfig() {
        return responseConfig;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private String headerName = "Idempotency-Key";
        private Duration ttl = Duration.ofHours(24);
        private ProcessorType processorType = ProcessorType.TRANSACTIONAL;
        private ConflictHandler conflictHandler;
        private boolean useFingerprint;
        private FingerprintPolicy fingerprintPolicy;
        private ResponseConfig responseConfig = ResponseConfig.defaults();

        private Builder() {}

        Builder headerName(String headerName) {
            this.headerName = headerName;
            return this;
        }

        Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        Builder processorType(ProcessorType processorType) {
            this.processorType = processorType;
            return this;
        }

        Builder conflictHandler(ConflictHandler conflictHandler) {
            this.conflictHandler = conflictHandler;
            return this;
        }

        Builder useFingerprint(boolean useFingerprint) {
            this.useFingerprint = useFingerprint;
            return this;
        }

        Builder fingerprintPolicy(FingerprintPolicy fingerprintPolicy) {
            this.fingerprintPolicy = fingerprintPolicy;
            return this;
        }

        Builder responseCacheConfig(ResponseConfig responseConfig) {
            this.responseConfig = responseConfig;
            return this;
        }

        TestOperationMetadata build() {
            return new TestOperationMetadata(this);
        }
    }
}
