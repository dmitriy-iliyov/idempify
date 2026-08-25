package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.TransactionAffinity;

/**
 * Decides what a request gets when it arrives for a key another request is still processing - the one case
 * where the library can neither run the operation nor replay a result, because there is no result yet.
 * <p>
 * Reachable only where the operation's record is kept outside the business transaction. In the transactional
 * branch a duplicate never reaches a handler: it blocks on the insert until the first request commits and
 * then replays what it stored.
 *
 * @see ConflictHandleStrategy
 */
public interface ConflictHandler extends TransactionAffinity {

    /**
     * Returns what the conflicting call should receive, or throws to refuse it - both are legitimate
     * outcomes, and which one applies is the handler's whole decision.
     *
     * @param context the contended key and the type a replayed result would have.
     * @param <T>     the type of the result.
     */
    <T> T handle(ConflictContext<T> context);
}
