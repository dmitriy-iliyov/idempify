package io.github.dmitriyiliyov.idempify.core;

/**
 * Which processor runs an operation, and with it two questions the processors answer differently: can a
 * duplicate that arrives while the first call is still running wait for it, and does the record of the outcome
 * commit together with the business effect?
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
    TRANSACTIONAL_BLOCKING,

    /**
     * The claim is a lease of its own, taken and released outside any transaction, so a duplicate does not
     * wait on it: meeting an unfinished operation becomes an outcome, and a {@code ConflictHandler} decides
     * what the duplicate gets. The business method and the record of its result still run in one transaction
     * and commit or roll back together; a failure takes the business effect away with the rollback and is
     * then kept on the record when the call site's {@code recordFor} names it, and releases the key otherwise.
     * <p>
     * The guarantee holds only while the record and the business data are under the same transaction manager.
     */
    TRANSACTIONAL_NON_BLOCKING,

    /**
     * Claims the key the same way as {@link #TRANSACTIONAL_NON_BLOCKING}, but runs the business method outside
     * any transaction of the library - for an effect no transaction of the application can take back, such as
     * a call to another service.
     * <p>
     * The guarantee is at-least-once. The effect and the record of the outcome are two separate writes, so an
     * outcome that was not recorded - the holder crashed, or the write failed - leaves the key claimed until the
     * lease runs out, and the next attempt then runs the operation again. A repeated effect is therefore the
     * caller's to absorb: the effect has to be idempotent downstream, for instance by passing the idempotency key
     * on to the service it calls. The lock TTL has to outlast the longest run of the business method, or a
     * duplicate takes over the expired lease and runs the method while the first call is still in it.
     */
    EXTERNAL_NON_BLOCKING;
}
