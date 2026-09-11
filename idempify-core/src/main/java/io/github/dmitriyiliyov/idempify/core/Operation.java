package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.time.Instant;
import java.util.UUID;

/**
 * One operation as the domain sees it - the stored row decoded, with the result and the response back as
 * objects.
 * <p>
 * Two of its states read like defects and are not. An expiry exists exactly on a
 * {@link OperationStatus#PROCESSED} operation: a claim is written without one, and a row still running is
 * never overwritten however long it has been there. And a {@code PROCESSED} operation may carry no response
 * at all - that means writing the response failed, not that the operation is unfinished, and a repeat is
 * answered from the result instead.
 */
public class Operation {

    private UUID idempotencyKey;
    private OperationStatus status;
    private Boolean isFirstAttempt;
    private Object result;
    private ResultType resultType;
    private Response response;
    private String fingerprint;
    private Instant expiresAt;
    private Instant createdAt;

    public Operation(UUID idempotencyKey,
                     OperationStatus status,
                     Boolean isFirstAttempt,
                     Object result,
                     ResultType resultType,
                     Response response,
                     String fingerprint,
                     Instant expiresAt,
                     Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.isFirstAttempt = isFirstAttempt;
        this.result = result;
        this.resultType = resultType;
        this.response = response;
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

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * The type the stored result is read back into. It travels with the operation but is never written to the
     * store - it comes from the call site on every read.
     */
    public ResultType getResultType() {
        return resultType;
    }

    public void setResultType(ResultType resultType) {
        this.resultType = resultType;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
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

    @Override
    public String toString() {
        return "Operation{" +
                "idempotencyKey=" + idempotencyKey +
                ", status=" + status +
                ", isFirstAttempt=" + isFirstAttempt +
                ", hasResult=" + (result != null) +
                ", resultType=" + resultType +
                ", hasResponse=" + (response != null) +
                ", fingerprint='" + fingerprint + '\'' +
                ", expiresAt=" + expiresAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
