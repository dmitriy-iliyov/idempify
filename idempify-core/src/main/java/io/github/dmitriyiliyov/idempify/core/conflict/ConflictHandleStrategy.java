package io.github.dmitriyiliyov.idempify.core.conflict;

/**
 * What to do when a request arrives for a key another request is still processing.
 * <p>
 * Only the handlers the library ships are named here. A hand-written one is not a constant of this enum but
 * an instance on the config: supplying it is what makes it custom, so a constant saying the same would only
 * repeat {@code ConflictConfig#getHandler() != null} and let the two disagree.
 * <p>
 * Every constant here is a decision already made; a call site that has not made one says so with
 * {@link ConflictHandleStrategyToggle#UNSELECTED} instead.
 *
 * @see ConflictHandler
 */
public enum ConflictHandleStrategy {

    /**
     * Poll the store until the original operation completes, then return its result - the duplicate request
     * ends up looking like a slow first one.
     */
    WAIT,

    /**
     * Fail the duplicate request immediately rather than waiting, leaving it to the client to retry.
     */
    REJECT
}
