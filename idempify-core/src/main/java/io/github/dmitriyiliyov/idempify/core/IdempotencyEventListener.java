package io.github.dmitriyiliyov.idempify.core;

/**
 * Defines the contract for listening to events related to idempotent operations.
 */
public interface IdempotencyEventListener {

    /**
     * Called when a duplicate request is detected.
     */
    void onDuplicate();

    /**
     * Called when a conflict is detected.
     */
    void onConflict();

    /**
     * Called when a fingerprint mismatch is detected.
     */
    void onFingerprintMismatch();

    /**
     * Called when an exception occurs during the processing of an idempotent operation.
     */
    void onException();

    /**
     * Called when an idempotent operation is successfully processed.
     */
    void onSuccess();

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
