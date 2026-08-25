package io.github.dmitriyiliyov.idempify.core.conflict;

import java.util.UUID;

/**
 * What a {@link ConflictHandler} is told about the conflict it has to resolve: which key is contended, and
 * what type the result must have if the handler produces one.
 *
 * @param <T> the type of the result.
 */
public interface ConflictContext<T> {

    /**
     * Returns the key another request is already processing.
     */
    UUID getIdempotencyKey();

    /**
     * Returns the type a replayed result is deserialized back into.
     */
    Class<T> getOperationResultType();
}
