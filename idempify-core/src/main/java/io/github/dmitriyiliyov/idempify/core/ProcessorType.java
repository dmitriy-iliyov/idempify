package io.github.dmitriyiliyov.idempify.core;

/**
 * Which processor runs an operation, and with it where the operation's record lives relative to the business
 * transaction.
 *
 * @see TypeAwareIdempotentProcessor
 */
public enum ProcessorType {

    /**
     * Record and business effect share one transaction and so commit or roll back together. A failed
     * operation leaves nothing behind, which also means its failure cannot be replayed.
     */
    TRANSACTIONAL,

    /**
     * Record is written outside the business transaction and guarded by an external lock, so it survives a
     * rollback. That is what makes a failed result replayable and a store without transactional
     * compare-and-swap usable.
     */
    LOCK_BASED
}
