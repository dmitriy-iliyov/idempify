package io.github.dmitriyiliyov.idempify.core;

/**
 * Describes what an {@link TransactionalOperationManager} decided should happen for the current call,
 * as an alternative to running the caller's business operation directly.
 *
 * @deprecated no longer produced or consumed anywhere - the manager returns an {@link java.util.Optional}
 * result instead. The distinction it was introduced for, between "no result" and "a result that is
 * {@code null}", is still unsolved and has to be settled before this can be deleted for good.
 */
@Deprecated(since = "0.0.1", forRemoval = true)
public interface OperationPropagation<T> {

    /**
     * Returns whether the caller must still produce a response — either by running the
     * original business operation or by invoking {@link #getCallback()} — instead of using
     * {@link #getResult()} directly.
     */
    boolean shouldPropagate();

    /**
     * Returns the already-available response for a duplicate call, or {@code null} if
     * {@link #shouldPropagate()} is {@code true}.
     */
    T getResult();

    /**
     * Returns the callback to run instead of the business operation (e.g. conflict handling),
     * or {@code null} if the business operation itself should run.
     */
    Callback<T> getCallback();

    /**
     * A unit of work that replaces the business operation for this call.
     *
     * @param <T> the type of the response.
     */
    interface Callback<T> extends TransactionAffinity {
        T call();
    }
}
