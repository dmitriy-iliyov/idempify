package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Read side of the operation store, shared by every concurrency strategy.
 * <p>
 * The store deals in rows, not in domain objects: it hands back a {@link RawOperation} exactly as it holds it
 * and leaves every decoding decision to the caller. That keeps a backend free of the library's serialization
 * SPI, and it keeps a caller that only wants part of a row - the recorded response, say - from paying for a
 * result it never reads.
 * <p>
 * Write access lives in the sub-interfaces because the guarantees differ:
 * {@link TransactionalOperationRepository} needs an atomic insert-or-fetch and compare-and-swap updates,
 * while a lock-based store gets mutual exclusion from an external lock instead.
 */
public interface OperationRepository {

    /**
     * Returns the row recorded under this key, or {@link Optional#empty()} if the key was never claimed
     * or its record has since been removed. Absence is an ordinary answer here, not a failure.
     */
    Optional<RawOperation> findByIdempotencyKey(UUID idempotencyKey);
}
