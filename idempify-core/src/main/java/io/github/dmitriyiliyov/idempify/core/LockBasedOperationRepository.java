package io.github.dmitriyiliyov.idempify.core;

/**
 * Write side of a store that gets its mutual exclusion from an external lock instead of from the store
 * itself, which is why it demands neither the atomic insert-or-fetch nor the compare-and-swap updates
 * {@link TransactionalOperationRepository} does.
 * <p>
 * Declared, not designed: it adds no methods to {@link OperationRepository} yet, because what they are
 * depends on how {@link LockBasedOperationManager} ends up taking the lock.
 */
public interface LockBasedOperationRepository extends OperationRepository {
}
