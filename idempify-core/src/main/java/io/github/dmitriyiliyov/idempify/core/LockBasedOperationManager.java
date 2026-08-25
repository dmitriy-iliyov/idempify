package io.github.dmitriyiliyov.idempify.core;

/**
 * Manages the lifecycle of an idempotent operation whose record is kept <em>outside</em> the business
 * transaction, so that it survives a rollback and a failed result stays replayable. Mutual exclusion comes
 * from an external lock rather than from an atomic upsert, which is what lets a store without transactional
 * compare-and-swap back it.
 * <p>
 * Declared, not designed: it carries no methods yet, and how the lock is taken and what happens when its
 * holder dies are still open questions.
 *
 * @see TransactionalOperationManager
 */
public interface LockBasedOperationManager {
}
