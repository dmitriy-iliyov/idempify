package io.github.dmitriyiliyov.idempify.core;

/**
 * The hook for metrics, logging and audit: one method per outcome an idempotent call can reach.
 * <p>
 * A method per outcome rather than one method taking an event type, so that an implementation counts what it
 * cares about without a {@code switch}, and a new outcome can be added with a {@code default} body instead of
 * breaking everyone. Implementations run on the request thread and must not throw or block.
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
     * Processing ended in an exception.
     */
    void onException();

    /**
     * The business operation ran and its result was stored - a first attempt, not a replay.
     */
    void onSuccess();

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
        public void onException() {}

        @Override
        public void onSuccess() {}
    };
}
