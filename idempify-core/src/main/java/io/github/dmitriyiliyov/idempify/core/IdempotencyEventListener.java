package io.github.dmitriyiliyov.idempify.core;

/**
 * The hook for metrics, logging and audit: one method per outcome an idempotent call can reach.
 * <p>
 * A method per outcome rather than one method taking an event type, so that an implementation counts what it
 * cares about without a {@code switch}, and a new outcome can be added with a {@code default} body instead of
 * breaking everyone. The reasons within one outcome are its argument instead: {@link #onFailure(FailureType)}
 * is one outcome, and {@link FailureType} is a dimension of it that an implementation records as it stands - a
 * tag, a log field - so a new reason reaches it without a new method.
 * <p>
 * Implementations run on the request thread and must not throw or block.
 */
public interface IdempotencyEventListener {

    /**
     * A call was answered from an earlier one's stored result - the work the library exists to avoid.
     */
    void onDuplicate();

    /**
     * A call arrived while another was still processing the same key, and was routed to a conflict handler.
     */
    void onConflict();

    /**
     * A key was reused with a request that fingerprints differently.
     */
    void onFingerprintMismatch();

    /**
     * The business operation threw and its key was released - by the rollback, or because the call site's
     * {@code recordFor} does not name the failure - a first attempt, and the next one runs the operation again.
     */
    void onBusinessFailure();

    /**
     * The call reached no outcome of its own for a reason outside the business method, which {@code type}
     * names. A failure that already has an event of its own - a fingerprint mismatch, a conflict - is not
     * reported here.
     */
    void onFailure(FailureType type);

    /**
     * The business operation ran and its result was stored - a first attempt, not a replay.
     */
    void onSuccess();

    /**
     * The business operation threw and its failure was kept on the record, so repeats of the key are answered
     * with it - a first attempt, not a replay.
     */
    void onExceptionallySuccess();

    /**
     * The listener installed when the application declares none, so that the processors can call the hook
     * unconditionally.
     */
    IdempotencyEventListener NOOP = new IdempotencyEventListener() {

        @Override
        public void onDuplicate() {}

        @Override
        public void onConflict() {}

        @Override
        public void onFingerprintMismatch() {}

        @Override
        public void onBusinessFailure() {}

        @Override
        public void onFailure(FailureType type) {}

        @Override
        public void onSuccess() {}

        @Override
        public void onExceptionallySuccess() {}
    };
}
