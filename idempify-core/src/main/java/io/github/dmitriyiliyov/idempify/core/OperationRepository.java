package io.github.dmitriyiliyov.idempify.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the row of a non-blocking operation without claiming it - the read half of
 * {@link NonBlockingOperationRepository}, which extends it. Both readers of that branch come through here: the
 * holder re-reading the row it has just claimed, and a call that met another's claim polling it while it waits
 * for the holder to finish.
 * <p>
 * The store deals in rows, not in domain objects: it hands back a row as it holds it and leaves every decoding
 * decision to the caller, which keeps a backend free of the library's serialization SPI. The one part of the
 * row it computes rather than holds is the time the record and its claim have left, counted by its own clock
 * at the moment of reading.
 * <p>
 * Taking no claim is the point: the row belongs to the caller that holds it, and a reader that claimed it
 * would stand in the way of the very completion it is waiting for.
 */
public interface OperationRepository {

    /**
     * Returns the row recorded under this key, or {@link Optional#empty()} if the key was never claimed
     * or its record has since been removed. Absence is an ordinary answer here, not a failure.
     */
    Optional<RawNonBlockingOperation> find(UUID idempotencyKey);
}
