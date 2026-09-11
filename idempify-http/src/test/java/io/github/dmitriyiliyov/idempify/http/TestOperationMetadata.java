package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.time.Duration;

final class TestOperationMetadata implements OperationMetadata {

    private final String headerName;
    private final Duration ttl;
    private final ConflictHandler conflictHandler;
    private final boolean useFingerprint;
    private final FingerprintPolicy fingerprintPolicy;
    private final ResponseConfig responseConfig;
    private final ProcessorType processorType;

    private TestOperationMetadata(Builder builder) {
        this.processorType = builder.processorType;
        this.headerName = builder.headerName;
        this.ttl = builder.ttl;
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

    @Override
    public ProcessorType getProcessorType() {
        return processorType;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private String headerName = IdempifyDefaults.HEADER_NAME;
        private Duration ttl = Duration.ofHours(24);
        private ConflictHandler conflictHandler;
        private boolean useFingerprint;
        private FingerprintPolicy fingerprintPolicy;
        private ResponseConfig responseConfig = ResponseConfig.defaults();
        private ProcessorType processorType = ProcessorType.TRANSACTIONAL;

        private Builder() {}

        Builder headerName(String headerName) {
            this.headerName = headerName;
            return this;
        }

        Builder ttl(Duration ttl) {
            this.ttl = ttl;
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

        Builder responseConfig(ResponseConfig responseConfig) {
            this.responseConfig = responseConfig;
            return this;
        }

        Builder processorType(ProcessorType processorType) {
            this.processorType = processorType;
            return this;
        }

        TestOperationMetadata build() {
            return new TestOperationMetadata(this);
        }
    }
}