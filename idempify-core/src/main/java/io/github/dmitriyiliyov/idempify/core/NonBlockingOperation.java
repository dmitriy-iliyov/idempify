package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.time.Instant;
import java.util.UUID;

public class NonBlockingOperation extends Operation {

    protected final UUID lockToken;
    protected final Long remainingLockMillis;

    public NonBlockingOperation(UUID idempotencyKey,
                                OperationStatus status,
                                Boolean isFirstAttempt,
                                Object result,
                                ResultType resultType,
                                Response response,
                                String fingerprint,
                                Long remainingMillis,
                                Instant createdAt,
                                UUID lockToken,
                                Long remainingLockMillis) {
        super(idempotencyKey, status, isFirstAttempt, result, resultType, response, fingerprint, remainingMillis, createdAt);
        this.lockToken = lockToken;
        this.remainingLockMillis = remainingLockMillis;
    }

    public static NonBlockingOperation ofContext(OperationContext context,
                                                 UUID lockToken) {
        return new NonBlockingOperation(
                context.getIdempotencyKey(),
                OperationStatus.IN_PROCESS,
                false,
                null,
                context.getResultType(),
                null,
                context.getFingerprint().orElse(null),
                null,
                null,
                lockToken,
                null
        );
    }

    public static NonBlockingOperation ofConflict(OperationContext context) {
        return new NonBlockingOperation(
                context.getIdempotencyKey(),
                OperationStatus.CONFLICT,
                false,
                null,
                context.getResultType(),
                null,
                context.getFingerprint().orElse(null),
                null,
                null,
                null,
                null
        );
    }

    /**
     * Whether this call met another call's claim on the key. It describes an attempt, never a stored row: a
     * conflict is built in memory and not read from the store.
     */
    public boolean hasConflict() {
        return OperationStatus.CONFLICT.equals(status);
    }

    /**
     * Whether a finished operation has outlived its TTL, so the key may be claimed again by a fresh attempt.
     * <p>
     * In this branch a failure kept on the record ({@link OperationStatus#EXCEPTIONALLY_PROCESSED}) is finished
     * too, and expires the same way; an operation still running never does.
     */
    @Override
    public boolean isExpired() {
        return OperationStatus.isProcessedStatus(status) && remainingMillis <= 0;
    }

    public UUID getLockToken() {
        return lockToken;
    }

    /**
     * Returns how long the claim on this row is still held, measured by the store's clock when the row was
     * read: zero or negative once the lease has run out, {@code null} when nobody holds it. Only a read fills
     * it - on a claim the store counts the lease from the lock TTL itself.
     */
    public Long getRemainingLockMillis() {
        return remainingLockMillis;
    }

    @Override
    public String toString() {
        return "NonBlockingOperation{" +
                "idempotencyKey=" + idempotencyKey +
                ", status=" + status +
                ", isFirstAttempt=" + isFirstAttempt +
                ", hasResult=" + (result != null) +
                ", resultType=" + resultType +
                ", hasResponse=" + (response != null) +
                ", fingerprint='" + fingerprint + '\'' +
                ", remainingMillis=" + remainingMillis +
                ", createdAt=" + createdAt +
                ", remainingLockMillis=" + remainingLockMillis +
                "} ";
    }
}
