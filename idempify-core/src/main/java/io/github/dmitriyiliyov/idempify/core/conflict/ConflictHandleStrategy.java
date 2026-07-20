package io.github.dmitriyiliyov.idempify.core.conflict;

/**
 * Defines the strategies for handling idempotency conflicts.
 */
public enum ConflictHandleStrategy {
    /**
     * Wait for the original operation to complete and then return its result.
     */
    WAIT,

    /**
     * Reject the duplicate request immediately.
     */
    REJECT,

    /**
     * Use a custom conflict handler.
     */
    CUSTOM
}
