package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.NonBlockingOperation;
import io.github.dmitriyiliyov.idempify.core.OperationStatus;

/**
 * Decides what a request gets when it arrives for a key another request is still processing - the one case
 * where the library can neither run the operation nor replay a result, because there is no result yet.
 * <p>
 * Reachable only in the non-blocking branch, where the claim is not the business transaction's and a
 * duplicate therefore meets the unfinished operation. In the blocking branch no duplicate reaches a handler:
 * it blocks on the insert until the first request commits and then replays what it stored.
 * <p>
 * A handler that answers with a stored result decodes it itself: the store hands back rows, and the type to
 * decode into arrives on the context rather than from the row.
 *
 * @see ConflictHandleStrategy
 */
public interface ConflictHandler {

    /**
     * Returns what the conflicting call should receive, or throws to refuse it - both are legitimate
     * outcomes, and which one applies is the handler's whole decision.
     * <p>
     * There is no third outcome: the call never runs the business method itself, so a handler that hands back
     * an operation still in progress, or nothing at all, leaves it with nothing to answer, and the call is
     * refused with {@link ConflictHandlerResultException}.
     *
     * @param context the contended key and the type a replayed result would have.
     * @return the completed operation whose outcome the call replays - {@link OperationStatus#PROCESSED} or
     *         {@link OperationStatus#EXCEPTIONALLY_PROCESSED}, never {@code null}.
     */
    NonBlockingOperation handle(ConflictContext context);
}
