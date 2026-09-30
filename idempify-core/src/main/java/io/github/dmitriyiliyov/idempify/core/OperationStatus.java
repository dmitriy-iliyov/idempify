package io.github.dmitriyiliyov.idempify.core;

import java.util.Set;

/**
 * Where an operation stands in its lifecycle.
 * <p>
 * Three of them describe a record and are written to the store; {@link #CONFLICT} describes the outcome of
 * an attempt that never got a record of its own, and exists only in memory.
 */
public enum OperationStatus {

    /**
     * A request claimed this key and its business operation has not finished yet. This is the state a row is
     * created in.
     */
    IN_PROCESS,

    /**
     * Another call holds the claim on this key, so this one can neither run the operation nor replay a
     * result - what it gets is the {@code ConflictHandler}'s decision.
     * <p>
     * Never persisted: it says what an attempt met, not what a record is. Only the non-blocking branch
     * assigns it, since only there a duplicate meets an unfinished operation instead of waiting for it - in
     * the blocking branch a duplicate blocks on the insert until the first call commits and then finds
     * {@link #PROCESSED}.
     */
    CONFLICT,

    /**
     * The business operation finished and its result is stored, so further requests with this key replay that
     * result instead of running anything - until the row's TTL expires.
     */
    PROCESSED,

    /**
     * The business operation threw, and the failure was kept on the record rather than releasing the key, so
     * repeats are answered with that outcome instead of running the operation again. Only the non-blocking
     * branch writes it: in the blocking branch the rollback takes the record away with the business effect.
     */
    EXCEPTIONALLY_PROCESSED;

    public static final Set<OperationStatus> PROCESSED_STATUSES = Set.of(
            OperationStatus.PROCESSED,
            OperationStatus.EXCEPTIONALLY_PROCESSED
    );

    public static boolean isProcessedStatus(OperationStatus status) {
        return PROCESSED_STATUSES.contains(status);
    }
}
