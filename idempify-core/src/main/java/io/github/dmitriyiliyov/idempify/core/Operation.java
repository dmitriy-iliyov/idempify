package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.time.Instant;
import java.util.UUID;

/**
 * One operation as the domain sees it - the stored row decoded, with the result and the response back as
 * objects.
 * <p>
 * Two of its states read like defects and are not. An expiry exists only on a finished operation -
 * {@link OperationStatus#PROCESSED}, and in the non-blocking branch
 * {@link OperationStatus#EXCEPTIONALLY_PROCESSED} as well: a claim is written without one, and a row still
 * running is never given one however long it has been there. And a {@code PROCESSED} operation may carry no
 * response at all - that means writing the response failed, not that the operation is unfinished, and a repeat
 * is answered from the result instead.
 */
public class Operation {

    protected UUID idempotencyKey;
    protected OperationStatus status;
    protected boolean isFirstAttempt;
    protected Object result;
    protected ResultType resultType;
    protected Response response;
    protected String fingerprint;
    protected Long remainingMillis;
    protected Instant createdAt;

    public Operation(UUID idempotencyKey,
                     OperationStatus status,
                     Boolean isFirstAttempt,
                     Object result,
                     ResultType resultType,
                     Response response,
                     String fingerprint,
                     Long remainingMillis,
                     Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.isFirstAttempt = isFirstAttempt;
        this.result = result;
        this.resultType = resultType;
        this.response = response;
        this.fingerprint = fingerprint;
        this.remainingMillis = remainingMillis;
        this.createdAt = createdAt;
    }

    public static Operation ofContext(OperationContext context) {
        return new Operation(
                context.getIdempotencyKey(),
                OperationStatus.IN_PROCESS,
                true,
                null,
                context.getResultType(),
                null,
                context.getFingerprint().orElse(null),
                null,
                null
        );
    }

    /**
     * Whether a finished operation has outlived its TTL, so the key may be claimed again by a fresh attempt.
     * <p>
     * Only a {@link OperationStatus#PROCESSED} operation can expire. One still
     * {@link OperationStatus#IN_PROCESS} is never overwritten however long it has been running, which also
     * makes this mutually exclusive with {@link NonBlockingOperation#hasConflict()}.
     * <p>
     * The status test is also what keeps the unboxing safe: an expiry is written only when an operation
     * completes, so a row that has one is {@code PROCESSED} by construction and a row without one never
     * reaches the comparison.
     */
    public boolean isExpired() {
        return OperationStatus.PROCESSED.equals(status) && remainingMillis <= 0;
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

    /**
     * Returns how long the operation stays replayable, measured by the store's clock when the row was read:
     * zero or negative once it has expired, {@code null} while it is still running. Only a read fills it - a
     * write ignores it, since the store counts the expiry from the TTL itself.
     */
    public Long getRemainingMillis() {
        return remainingMillis;
    }

    public void setRemainingMillis(Long remainingMillis) {
        this.remainingMillis = remainingMillis;
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
                ", remainingMillis=" + remainingMillis +
                ", createdAt=" + createdAt +
                '}';
    }
}
