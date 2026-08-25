package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of an idempotent operation whose bookkeeping shares a transaction with the business
 * operation itself.
 * <p>
 * That sharing is what makes the record and the business effect impossible to diverge - they commit or roll
 * back together - and it is also why a concurrent duplicate cannot be detected here: until the first request
 * commits, its row is invisible to everyone else, and by the time it commits the operation is already
 * complete. A duplicate therefore blocks on the insert and then replays the result, rather than being routed
 * to a conflict handler.
 */
public interface TransactionalOperationManager {

    /**
     * Claims the key for this call, or returns the result of an operation that already completed under it.
     *
     * @param context  what identifies the current call.
     * @param metadata the resolved settings of the call site.
     * @param <T>      the type of the result.
     * @return the stored result to replay, or empty if the caller must run the business operation itself.
     */
    <T> Optional<T> startOrReply(OperationContext<T> context, OperationMetadata metadata);

    /**
     * Records the result of an operation the caller has just run, moving it out of the in-process state.
     *
     * @param idempotencyKey the key of the operation to complete.
     * @param result         the result to store.
     * @param <T>            the type of the result.
     * @return the same result, so the caller can return it directly.
     */
    <T> T complete(UUID idempotencyKey, T result);
}
