package io.github.dmitriyiliyov.idempify.core;

/**
 * Why a call reached no outcome of its own: the reason lies outside the business method, so it is reported by
 * {@link IdempotencyEventListener#onFailure(FailureType)} rather than counted as a failure of the operation.
 * <p>
 * The types split by what the operator does next, and above all by whether the business method ran: before it,
 * nothing happened and a repeat is safe; after it, the effect may stand without a record of it.
 */
public enum FailureType {

    /**
     * {@code @Idempotent} sits somewhere other than an entry point: the call was made inside another idempotent
     * operation, or inside a transaction the processor did not open.
     */
    MISPLACED,

    /**
     * The thread was interrupted - by a shutdown or a cancellation.
     */
    INTERRUPTED,

    /**
     * The library failed before the business method ran - claiming the key, reading or decoding its record,
     * opening the transaction, committing a replay - so the operation had no effect.
     */
    FAILED_BEFORE_RUN,

    /**
     * The business method ran, returning or throwing, and its outcome could not be recorded. Under
     * {@link ProcessorType#EXTERNAL_NON_BLOCKING} its effect stays, and the key is held until the lease runs out,
     * after which a repeat runs the operation again.
     */
    OUTCOME_NOT_RECORDED
}
