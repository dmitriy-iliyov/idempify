package io.github.dmitriyiliyov.idempify.core;

import java.time.Duration;
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
     * @return what the store holds for this key, never {@code null}: a {@link OperationStatus#PROCESSED}
     *         operation whose result is there to be replayed, or one this call has just claimed and
     *         therefore must run itself. A claim carries no expiry yet - that is
     *         {@link #complete}'s to write.
     */
    <T> OperationDetail<T> startOrReply(OperationContext<T> context, OperationMetadata metadata);

    /**
     * Records the result of an operation the caller has just run, moving it out of the in-process state and
     * giving it the expiry it will be replayed under.
     * <p>
     * The TTL arrives here, at completion, rather than at the claim, because an operation that ran longer
     * than its own TTL would otherwise commit a row that is already stale: no replay would ever be served
     * from it, and the response would miss the cache too. The expiry is therefore counted from the moment
     * the result exists.
     *
     * @param idempotencyKey the key of the operation to complete.
     * @param ttl            how long the stored result stays replayable, counted from now.
     * @param result         the result to store.
     * @param <T>            the type of the result.
     * @return the completed operation, never {@code null} - the same result, plus what the store decided
     *         about it, its expiry above all.
     */
    <T> OperationDetail<T> complete(UUID idempotencyKey, Duration ttl, T result);
}
