package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Read side of the operation store, shared by every concurrency strategy.
 * <p>
 * Write access lives in the sub-interfaces because the guarantees differ:
 * {@link TransactionalOperationRepository} needs an atomic insert-or-fetch and compare-and-swap updates,
 * while a lock-based store gets mutual exclusion from an external lock instead.
 */
public interface OperationRepository {

    /**
     * Returns the operation recorded under this key, or {@link Optional#empty()} if the key was never claimed
     * or its record has since been removed. Absence is an ordinary answer here, not a failure.
     */
    Optional<Operation> findByIdempotencyKey(UUID idempotencyKey);
}
