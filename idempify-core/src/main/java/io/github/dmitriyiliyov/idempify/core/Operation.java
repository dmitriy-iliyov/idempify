package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

public class Operation {

    private UUID idempotencyKey;
    private OperationStatus status;
    private Boolean isFirstAttempt;
    private String result;
    private String fingerprint;
    private Instant expiresAt;
    private Instant createdAt;

    public Operation(UUID idempotencyKey,
                     OperationStatus status,
                     Boolean isFirstAttempt,
                     String result,
                     String fingerprint,
                     Instant expiresAt,
                     Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.isFirstAttempt = isFirstAttempt;
        this.result = result;
        this.fingerprint = fingerprint;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    /**
     * Whether an earlier request claimed this key and is still running it: the operation has not finished,
     * and the write that produced this row was not the one that created it.
     */
    public boolean hasConflict() {
        return OperationStatus.IN_PROCESS.equals(status) && !isFirstAttempt;
    }

    /**
     * Whether a finished operation has outlived its TTL, so the key may be claimed again by a fresh attempt.
     * <p>
     * Only a {@link OperationStatus#PROCESSED} operation can expire. One still
     * {@link OperationStatus#IN_PROCESS} is never overwritten however long it has been running, which also
     * makes this mutually exclusive with {@link #hasConflict()}.
     * <p>
     * The status test is also what keeps the expiry dereference safe: an expiry is written only when an
     * operation completes, so a row that has one is {@code PROCESSED} by construction and a row without one
     * never reaches the comparison.
     *
     * @param now the current instant, taken from the caller's clock.
     */
    public boolean isExpired(Instant now) {
        return OperationStatus.PROCESSED.equals(status) && expiresAt.isBefore(now);
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
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
