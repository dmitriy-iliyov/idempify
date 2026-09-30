package io.github.dmitriyiliyov.idempify.core;

/**
 * Which processor runs an operation, and with it the one question the two branches answer differently: can a
 * duplicate that arrives while the first call is still running wait for it?
 *
 * @see TypeAwareIdempotentProcessor
 */
public enum ProcessorType {

    /**
     * The claim on a key is the business transaction's own, so a duplicate blocks on it until the first call
     * commits and is then answered with its result - it never reaches a conflict handler. Record and
     * business effect commit or roll back together, which is also why a failed operation leaves nothing
     * behind to replay.
     */
    BLOCKING,

    /**
     * The claim is a record of its own, taken and released apart from the business transaction, so a
     * duplicate cannot wait on it: meeting an unfinished operation becomes an outcome, and a
     * {@code ConflictHandler} decides what the duplicate gets. That is what lets a store with no
     * transaction-scoped lock back the library, and what lets the record outlive a rollback.
     */
    NON_BLOCKING
}
