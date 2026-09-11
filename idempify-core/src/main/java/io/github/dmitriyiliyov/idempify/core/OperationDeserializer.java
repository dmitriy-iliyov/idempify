package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

/**
 * Reads a stored row back into an {@link Operation}.
 * <p>
 * The declared type of the result comes from the caller rather than from the row: it belongs to the call site,
 * and storing it beside the result would only duplicate what the caller already holds.
 */
public interface OperationDeserializer {

    /**
     * @param operation the row as the store returned it, or {@code null}.
     * @param resultType the type to read the result back into, or {@code null} for a caller that does not want
     *                   the result - the returned operation then carries none, and a result that cannot be read
     *                   back cannot fail the read either.
     */
    Operation deserialize(RawOperation operation, ResultType resultType);
}
