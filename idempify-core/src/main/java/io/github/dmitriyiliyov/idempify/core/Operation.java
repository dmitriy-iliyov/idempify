package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

public class Operation {

    private UUID idempotencyKey;
    private OperationState state;
    private Boolean isFirstAttempt;
    private String result;
    private String fingerprint;
    private Instant expiresAt;
    private Instant createdAt;

    public Operation(UUID idempotencyKey,
                     OperationState state,
                     Boolean isFirstAttempt,
                     String result,
                     String fingerprint,
                     Instant expiresAt,
                     Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.state = state;
        this.isFirstAttempt = isFirstAttempt;
        this.result = result;
        this.fingerprint = fingerprint;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean hasConflict() {
        return OperationState.IN_PROCESS.equals(state) && !isFirstAttempt;
    }

    public boolean isExpired(Instant now) {
        return OperationState.PROCESSED.equals(state) && expiresAt.isBefore(now);
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public OperationState getState() {
        return state;
    }

    public void setState(OperationState state) {
        this.state = state;
    }

    public Boolean isFirstAttempt() {
        return isFirstAttempt;
    }

    public void setFirstAttempt(Boolean firstAttempt) {
        isFirstAttempt = firstAttempt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
