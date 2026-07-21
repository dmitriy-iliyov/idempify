package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class OperationMetadata {

    private final UUID idempotencyKey;
    private final long ttl;
    private final TimeUnit timeUnit;
    private final ConflictHandleStrategy conflictHandleStrategy;
    private final Class<? extends ConflictHandler> conflictHandlerClass;
    private final boolean useFingerprint;
    private final String fingerprint;
    private final Class<? extends FingerprintPolicy> fingerprintPolicyClass;

    private OperationMetadata(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.ttl = builder.ttl;
        this.timeUnit = builder.timeUnit;

        this.conflictHandleStrategy = builder.conflictHandleStrategy;
        this.conflictHandlerClass = builder.conflictHandlerClass;
        if (ConflictHandleStrategy.CUSTOM.equals(this.conflictHandleStrategy)) {
            Objects.requireNonNull(conflictHandlerClass, "conflictHandlerClass cannot be null when conflictHandleStrategy is CUSTOM");
        }

        this.useFingerprint = builder.useFingerprint;
        this.fingerprint = builder.fingerprint;
        this.fingerprintPolicyClass = builder.fingerprintPolicyClass;
        if (this.useFingerprint) {
            Objects.requireNonNull(fingerprint, "fingerprint cannot be null when useFingerprint is true");
            Objects.requireNonNull(fingerprintPolicyClass, "fingerprintPolicyClass cannot be null when useFingerprint is true");
        }
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getTtl() {
        return ttl;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public ConflictHandleStrategy getConflictHandleStrategy() {
        return conflictHandleStrategy;
    }

    public Class<? extends ConflictHandler> getConflictHandlerClass() {
        return conflictHandlerClass;
    }

    public boolean useFingerprint() {
        return useFingerprint;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Class<? extends FingerprintPolicy> getFingerprintPolicyClass() {
        return fingerprintPolicyClass;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID idempotencyKey;
        private long ttl;
        private TimeUnit timeUnit;
        private ConflictHandleStrategy conflictHandleStrategy;
        private Class<? extends ConflictHandler> conflictHandlerClass;
        private boolean useFingerprint;
        private String fingerprint;
        private Class<? extends FingerprintPolicy> fingerprintPolicyClass;

        private Builder() {}

        public Builder idempotencyKey(UUID idempotencyKey) {
            this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
            return this;
        }

        public Builder ttl(Long ttl) {
            Objects.requireNonNull(ttl, "ttl cannot be null");
            if (ttl < 0) {
                throw new IllegalArgumentException("ttl cannot be negative");
            }
            this.ttl = ttl;
            return this;
        }

        public Builder timeUnit(TimeUnit timeUnit) {
            this.timeUnit = Objects.requireNonNull(timeUnit, "timeUnit cannot be null");
            return this;
        }

        public Builder conflictHandleStrategy(ConflictHandleStrategy conflictHandleStrategy) {
            this.conflictHandleStrategy = Objects.requireNonNull(conflictHandleStrategy, "conflictHandleStrategy cannot be null");
            return this;
        }

        public Builder conflictHandlerClass(Class<? extends ConflictHandler> conflictHandlerClass) {
            this.conflictHandlerClass = conflictHandlerClass;
            return this;
        }

        public Builder useFingerprint(boolean useFingerprint) {
            this.useFingerprint = useFingerprint;
            return this;
        }

        public Builder fingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        public Builder fingerprintPolicyClass(Class<? extends FingerprintPolicy> fingerprintPolicyClass) {
            this.fingerprintPolicyClass = fingerprintPolicyClass;
            return this;
        }

        public OperationMetadata build() {
            return new OperationMetadata(this);
        }
    }
}
