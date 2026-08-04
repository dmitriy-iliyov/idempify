package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.config.ResponseCacheConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.time.Duration;

final class TestOperationMetadata implements OperationMetadata {

    private final String headerName;
    private final Duration ttl;
    private final ConflictHandler conflictHandler;
    private final boolean useFingerprint;
    private final FingerprintPolicy fingerprintPolicy;
    private final ResponseCacheConfig responseCacheConfig;

    private TestOperationMetadata(Builder builder) {
        this.headerName = builder.headerName;
        this.ttl = builder.ttl;
        this.conflictHandler = builder.conflictHandler;
        this.useFingerprint = builder.useFingerprint;
        this.fingerprintPolicy = builder.fingerprintPolicy;
        this.responseCacheConfig = builder.responseCacheConfig;
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
    public ResponseCacheConfig getResponseCacheConfig() {
        return responseCacheConfig;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private String headerName = "Idempotency-Key";
        private Duration ttl = Duration.ofHours(24);
        private ConflictHandler conflictHandler;
        private boolean useFingerprint;
        private FingerprintPolicy fingerprintPolicy;
        private ResponseCacheConfig responseCacheConfig = ResponseCacheConfig.defaults();

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

        Builder responseCacheConfig(ResponseCacheConfig responseCacheConfig) {
            this.responseCacheConfig = responseCacheConfig;
            return this;
        }

        TestOperationMetadata build() {
            return new TestOperationMetadata(this);
        }
    }
}
