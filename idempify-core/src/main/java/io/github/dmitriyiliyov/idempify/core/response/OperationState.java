package io.github.dmitriyiliyov.idempify.core.response;

import java.time.Instant;

/**
 * What the repository holds for one operation, as far as anyone downstream needs to know.
 */
public interface OperationState {

    /**
     * When the record stops being valid. Nothing derived from it - a cached response above all - may outlive
     * this moment.
     */
    Instant getExpiresAt();

    /**
     * Whether the result was read back from the repository rather than produced by this call. A replay is
     * already stored, so there is nothing new to write anywhere.
     */
    boolean replayed();
}
