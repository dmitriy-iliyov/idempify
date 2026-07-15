package io.github.dmitriyiliyov.springidempotency.core;

import java.time.Instant;
import java.util.UUID;

public final class Operation {

    private UUID idempotencyKey;
    private OperationState state;
    private String response;
    private String fingerprint;
    private Instant expiresAt;
    private Instant createdAt;

    public Operation(UUID idempotencyKey, OperationState state, String response, String fingerprint, Instant expiresAt, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.state = state;
        this.response = response;
        this.fingerprint = fingerprint;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
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

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
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
